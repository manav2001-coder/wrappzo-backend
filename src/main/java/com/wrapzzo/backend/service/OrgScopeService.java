package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a company user's OrgScope -- the one place the "own unit and everything under it"
 * rule lives, so every list and every review check asks the same question (orders, Quick Gift
 * approvals, artwork review). Uncached: a subtree query on the GIN-indexed ancestor_ids is
 * cheap, and a stale scope after a transfer is the kind of bug nobody notices.
 */
@Service
@RequiredArgsConstructor
public class OrgScopeService {

    /** Granted to Company Admin by default (V36): see every org unit in the company. */
    public static final String ALL_UNITS_KEY = "org.scope.all";

    private final AccessControlCacheService accessControlCacheService;
    private final OrgUnitRepository orgUnitRepository;

    @Transactional(readOnly = true)
    public OrgScope resolve(User user) {
        if (accessControlCacheService.resolveEffectiveKeys(user).contains(ALL_UNITS_KEY)) {
            return new OrgScope(user.getId(), null);
        }
        if (user.getOrgUnit() == null) {
            return new OrgScope(user.getId(), Set.of());
        }
        Set<UUID> subtree = orgUnitRepository.findSelfAndDescendants(user.getOrgUnit().getId()).stream()
                .map(OrgUnit::getId)
                .collect(Collectors.toUnmodifiableSet());
        return new OrgScope(user.getId(), subtree);
    }
}
