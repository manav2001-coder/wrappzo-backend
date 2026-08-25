package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import com.wrapzzo.backend.model.AccessEffect;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserAccessControlRepository;
import com.wrapzzo.backend.repository.UserAccessKeyEffect;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caches resolved access-control data in Redis, split by role and by user so
 * that editing a role invalidates exactly one key regardless of how many
 * users hold it, and editing a user's overrides invalidates exactly one key
 * for that user. Postgres remains the source of truth; a cache miss (empty
 * cache, or after an invalidation) falls back to a DB read and repopulates.
 */
@Service
@RequiredArgsConstructor
public class AccessControlCacheService {

    /** Safety net in case an invalidation is ever missed — not the primary invalidation mechanism. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String ROLE_KEY_PREFIX = "role-permissions:";
    private static final String USER_OVERRIDE_KEY_PREFIX = "user-overrides:";

    private final RoleAccessControlRepository roleAccessControlRepository;
    private final UserAccessControlRepository userAccessControlRepository;
    private final RoleRepository roleRepository;
    private final AccessControlRepository accessControlRepository;
    private final StringRedisTemplate redisTemplate;

    /** Role's granted keys, plus this user's GRANT overrides, minus their REVOKE overrides. */
    public Set<String> resolveEffectiveKeys(UUID userId, UUID roleId) {
        Set<String> effective = new HashSet<>(getRolePermissionKeys(roleId));
        for (Map.Entry<String, AccessEffect> override : getUserOverrides(userId).entrySet()) {
            if (override.getValue() == AccessEffect.GRANT) {
                effective.add(override.getKey());
            } else {
                effective.remove(override.getKey());
            }
        }
        return effective;
    }

    @Transactional(readOnly = true)
    public Set<String> getRolePermissionKeys(UUID roleId) {
        String redisKey = ROLE_KEY_PREFIX + roleId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return decodeKeySet(cached);
        }
        Set<String> keys = new HashSet<>(isPlatformSuperAdmin(roleId)
                ? accessControlRepository.findByType(AccessControlType.FUNCTIONAL).stream().map(AccessControl::getKey).toList()
                : roleAccessControlRepository.findAccessControlKeysByRoleId(roleId));
        redisTemplate.opsForValue().set(redisKey, encodeKeySet(keys), CACHE_TTL);
        return keys;
    }

    /**
     * Super Admin gets every FUNCTIONAL key dynamically rather than an explicit grant list
     * that has to be remembered and updated every time a new module ships a new key.
     */
    private boolean isPlatformSuperAdmin(UUID roleId) {
        return roleRepository.findById(roleId).map(Role::isPlatformSuperAdmin).orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, AccessEffect> getUserOverrides(UUID userId) {
        String redisKey = USER_OVERRIDE_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return decodeOverrides(cached);
        }
        List<UserAccessKeyEffect> overrides = userAccessControlRepository.findKeyEffectsByUserId(userId);
        Map<String, AccessEffect> overrideMap =
                overrides.stream().collect(Collectors.toMap(UserAccessKeyEffect::getKey, UserAccessKeyEffect::getEffect));
        redisTemplate.opsForValue().set(redisKey, encodeOverrides(overrideMap), CACHE_TTL);
        return overrideMap;
    }

    /** Call whenever a role's granted access-control keys change. */
    public void invalidateRolePermissions(UUID roleId) {
        redisTemplate.delete(ROLE_KEY_PREFIX + roleId);
    }

    /** Call whenever a specific user's access-control overrides change. */
    public void invalidateUserOverrides(UUID userId) {
        redisTemplate.delete(USER_OVERRIDE_KEY_PREFIX + userId);
    }

    private static String encodeKeySet(Set<String> keys) {
        return String.join(",", keys);
    }

    private static Set<String> decodeKeySet(String encoded) {
        if (encoded.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(encoded.split(",")));
    }

    private static String encodeOverrides(Map<String, AccessEffect> overrides) {
        return overrides.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static Map<String, AccessEffect> decodeOverrides(String encoded) {
        Map<String, AccessEffect> map = new HashMap<>();
        if (encoded.isEmpty()) {
            return map;
        }
        for (String pair : encoded.split(",")) {
            String[] parts = pair.split("=", 2);
            map.put(parts[0], AccessEffect.valueOf(parts[1]));
        }
        return map;
    }
}
