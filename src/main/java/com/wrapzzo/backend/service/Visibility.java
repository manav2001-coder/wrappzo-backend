package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.User;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which records one user can see. A record is visible when the user
 * <ul>
 *   <li>is company-wide (placed at the company itself, or platform staff), or</li>
 *   <li>owns it, or</li>
 *   <li>its owner is below them in the reporting line ({@code teamUserIds}), or</li>
 *   <li>it is stamped with an org unit in {@code orgUnitIds}: the units below their own, plus
 *       the units they additionally cover and everything below those.</li>
 * </ul>
 * Peers in the same unit don't see each other's records unless one reports to the other, so
 * whoever sits at the bottom of the tree with nobody reporting to them sees only their own.
 * Resolved and cached by VisibilityService; immutable.
 */
public record Visibility(UUID userId, boolean companyWide, Set<UUID> orgUnitIds, Set<UUID> teamUserIds) {

    /** Stands in for "no unit" in an IN clause, which can't be empty -- no row has this id. */
    private static final UUID NO_MATCH = new UUID(0L, 0L);

    public Visibility {
        orgUnitIds = Set.copyOf(orgUnitIds);
        teamUserIds = Set.copyOf(teamUserIds);
    }

    public static Visibility everything(UUID userId) {
        return new Visibility(userId, true, Set.of(), Set.of());
    }

    public boolean canSee(UUID ownerId, UUID orgUnitId) {
        return companyWide
                || userId.equals(ownerId)
                || (ownerId != null && teamUserIds.contains(ownerId))
                || (orgUnitId != null && orgUnitIds.contains(orgUnitId));
    }

    public boolean canSee(User owner, OrgUnit orgUnit) {
        return canSee(owner != null ? owner.getId() : null, orgUnit != null ? orgUnit.getId() : null);
    }

    /** A user record counts as owned by that user and stamped with their own unit. */
    public boolean canSeeUser(User user) {
        return canSee(user, user.getOrgUnit());
    }

    /** Nothing beyond one's own records -- e.g. the dashboard then shows "your" figures, not a team's. */
    public boolean ownRecordsOnly() {
        return !companyWide && orgUnitIds.isEmpty() && teamUserIds.isEmpty();
    }

    /** Owners whose records are visible, for an IN clause: oneself and one's reporting line. Never empty. */
    public Set<UUID> peopleForQuery() {
        Set<UUID> people = new HashSet<>(teamUserIds);
        people.add(userId);
        return people;
    }

    /** Visible org units for an IN clause. Never empty. */
    public Set<UUID> orgUnitsForQuery() {
        return orgUnitIds.isEmpty() ? Set.of(NO_MATCH) : orgUnitIds;
    }
}
