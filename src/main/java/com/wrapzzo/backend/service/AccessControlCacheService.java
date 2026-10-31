package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyAccessControlRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Resolves a user's effective access-control keys: the templates mapped to their role plus the
 * templates mapped to them directly, each followed through the templates it includes (see
 * {@link TemplateGraph}). The platform Super Admin role instead holds every FUNCTIONAL key.
 *
 * <p>Cached in Redis per user. Invalidation is generational, like VisibilityService: the
 * generation is part of every cache key and {@link #invalidateAfterCommit(UUID)} bumps it, so any change to a
 * template, a mapping or a user's role refreshes everyone without tracking who it touches. Old
 * generations expire on their own. If Redis is unavailable the keys are computed directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlCacheService {

    /** Safety net in case an invalidation is ever missed -- not the primary invalidation mechanism. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String GENERATION_KEY = "permission-generation";
    private static final String KEY_PREFIX = "permissions:";

    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final AccessControlRepository accessControlRepository;
    private final CompanyAccessControlRepository companyAccessControlRepository;
    private final StringRedisTemplate redisTemplate;

    /** Effective keys of a user whose id is all the caller has (the JWT filter). */
    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveKeys(UUID userId) {
        String cacheKey = null;
        try {
            cacheKey = KEY_PREFIX + generation(GENERATION_KEY) + ":" + userId;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Set<String> keys = decodeIfFresh(cached);
                if (keys != null) {
                    return keys;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Permission cache unavailable, computing directly: {}", e.getMessage());
            cacheKey = null;
        }
        User user = userRepository.findById(userId).orElse(null);
        Set<String> keys = user == null ? Set.of() : compute(user);
        if (cacheKey != null && user != null) {
            try {
                String companyId = user.getCompany() != null ? user.getCompany().getId().toString() : "";
                String companyGeneration = user.getCompany() != null ? generation(companyGenerationKey(user.getCompany().getId())) : "";
                redisTemplate.opsForValue().set(cacheKey, companyId + "|" + companyGeneration + "|" + String.join(",", keys), CACHE_TTL);
            } catch (RuntimeException e) {
                log.warn("Could not cache permissions: {}", e.getMessage());
            }
        }
        return keys;
    }

    /** The cached keys, unless their company's generation has moved on since they were stored. */
    private Set<String> decodeIfFresh(String cached) {
        String[] parts = cached.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }
        if (!parts[0].isEmpty() && !parts[1].equals(generation(companyGenerationKey(UUID.fromString(parts[0]))))) {
            return null;
        }
        return decode(parts[2]);
    }

    /** For call sites that already hold the user. */
    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveKeys(User user) {
        return resolveEffectiveKeys(user.getId());
    }

    private Set<String> compute(User user) {
        if (user.getRole().isPlatformSuperAdmin()) {
            return new HashSet<>(accessControlRepository.findByType(AccessControlType.FUNCTIONAL).stream().map(AccessControl::getKey).toList());
        }
        Set<UUID> roots = new HashSet<>(templateRepository.findTemplateIdsByRoleId(user.getRole().getId()));
        roots.addAll(templateRepository.findTemplateIdsByUserId(user.getId()));
        Set<String> keys = roots.isEmpty() ? new HashSet<>() : loadGraph().resolve(roots);
        if (user.getCompany() != null) {
            // A system template may carry keys a company never registered, or switched off (the feature
            // master); a company's people hold only the features it has on.
            keys.retainAll(companyAccessControlRepository.findActiveKeysByCompanyId(user.getCompany().getId()));
        }
        return keys;
    }

    /** Every inclusion edge plus the direct keys of every template reachable by one -- read whole, the tables are small. */
    @Transactional(readOnly = true)
    public TemplateGraph loadGraph() {
        Map<UUID, Set<UUID>> edges = new HashMap<>();
        for (Object[] row : templateRepository.findAllReferenceEdges()) {
            edges.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((UUID) row[1]);
        }
        Set<UUID> ids = new HashSet<>(edges.keySet());
        edges.values().forEach(ids::addAll);
        ids.addAll(templateRepository.findAll().stream().map(t -> t.getId()).toList());
        Map<UUID, Set<String>> directKeys = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : templateRepository.findDirectKeys(ids)) {
                directKeys.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((String) row[1]);
            }
        }
        return new TemplateGraph(edges, directKeys);
    }

    /** Refreshes one company's keys once the surrounding transaction commits; no company (a system template) means everyone's. */
    public void invalidateAfterCommit(Company company) {
        invalidateAfterCommit(company != null ? company.getId() : null);
    }

    public void invalidateAfterCommit(UUID companyId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate(companyId);
                }
            });
        } else {
            invalidate(companyId);
        }
    }

    /** Bumps one company's generation (or the global one for {@code null}), orphaning its cached entries. */
    public void invalidate(UUID companyId) {
        try {
            redisTemplate.opsForValue().increment(companyId == null ? GENERATION_KEY : companyGenerationKey(companyId));
        } catch (RuntimeException e) {
            log.warn("Could not invalidate cached permissions: {}", e.getMessage());
        }
    }

    /**
     * Drops every cached grant. Run at startup (PermissionCacheStartupReset): a deploy's
     * migrations change grants straight in Postgres, bypassing the invalidation above.
     */
    public long clearAll() {
        invalidate(null);
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted : 0;
    }

    private String generation(String key) {
        String generation = redisTemplate.opsForValue().get(key);
        return generation != null ? generation : "0";
    }

    private static String companyGenerationKey(UUID companyId) {
        return GENERATION_KEY + ":" + companyId;
    }

    private static Set<String> decode(String encoded) {
        return encoded.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(encoded.split(",")));
    }
}
