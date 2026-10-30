package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Resolves a user's {@link Visibility} -- the data-visibility engine, independent of what they
 * may do (permissions decide that). Every list and lookup of user-owned records goes through it:
 * lists pass {@link Visibility#peopleForQuery()} / {@link Visibility#orgUnitsForQuery()} into
 * their query, single lookups check {@link Visibility#canSee}.
 *
 * <p>Cached in Redis per user. Invalidation is generational: each company has a generation
 * counter that is part of every cache key, and any structural change in the company bumps it
 * (CompanyStructureChangedEvent, after commit) -- so one change, e.g. an MR's manager changing,
 * correctly refreshes everyone whose team or units it touches without tracking who that is.
 * Old generations simply expire. If Redis is unavailable, visibility is computed directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisibilityService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String GENERATION_KEY_PREFIX = "visibility-generation:";
    private static final String CACHE_KEY_PREFIX = "visibility:";

    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public Visibility resolve(User user) {
        if (user.getCompany() == null) {
            return Visibility.everything(user.getId());
        }
        UUID companyId = user.getCompany().getId();
        String cacheKey = null;
        try {
            cacheKey = CACHE_KEY_PREFIX + companyId + ":" + generation(companyId) + ":" + user.getId();
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return decode(user.getId(), cached);
            }
        } catch (RuntimeException e) {
            log.warn("Visibility cache unavailable, computing directly: {}", e.getMessage());
            cacheKey = null;
        }
        Visibility visibility = compute(user);
        if (cacheKey != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, encode(visibility), CACHE_TTL);
            } catch (RuntimeException e) {
                log.warn("Could not cache visibility: {}", e.getMessage());
            }
        }
        return visibility;
    }

    /** Drops every cached visibility in the company -- after the change that prompted it has committed. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onCompanyStructureChanged(CompanyStructureChangedEvent event) {
        try {
            redisTemplate.opsForValue().increment(GENERATION_KEY_PREFIX + event.companyId());
        } catch (RuntimeException e) {
            log.warn("Could not invalidate visibility for company {}: {}", event.companyId(), e.getMessage());
        }
    }

    private Visibility compute(User user) {
        if (user.isCompanyWide()) {
            return Visibility.everything(user.getId());
        }
        Set<UUID> units = new HashSet<>();
        // Below their own unit -- not the unit itself, so peers there stay out of each other's records...
        if (user.getOrgUnit() != null) {
            units.addAll(orgUnitRepository.findDescendantIds(user.getOrgUnit().getId()));
        }
        // ...but a unit they additionally cover is theirs entirely.
        for (OrgUnit covered : user.getExtraOrgUnits()) {
            orgUnitRepository.findSelfAndDescendants(covered.getId()).forEach(unit -> units.add(unit.getId()));
        }
        Set<UUID> team = new HashSet<>(userRepository.findReportingLineIds(user.getId()));
        team.remove(user.getId());
        return new Visibility(user.getId(), false, units, team);
    }

    private String generation(UUID companyId) {
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY_PREFIX + companyId);
        return generation != null ? generation : "0";
    }

    // "1|unit,unit|user,user" -- company-wide flag, visible units, reporting line.
    private static String encode(Visibility visibility) {
        return (visibility.companyWide() ? "1" : "0") + "|" + join(visibility.orgUnitIds()) + "|" + join(visibility.teamUserIds());
    }

    private static Visibility decode(UUID userId, String encoded) {
        String[] parts = encoded.split("\\|", -1);
        return new Visibility(userId, "1".equals(parts[0]), split(parts[1]), split(parts[2]));
    }

    private static String join(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static Set<UUID> split(String joined) {
        return joined.isEmpty() ? Set.of() : Arrays.stream(joined.split(",")).map(UUID::fromString).collect(Collectors.toSet());
    }
}
