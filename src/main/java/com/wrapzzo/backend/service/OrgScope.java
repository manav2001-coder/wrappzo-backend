package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.User;
import java.util.Set;
import java.util.UUID;

/**
 * What part of the org tree a company user may see, resolved once per request by
 * OrgScopeService. Three cases, mirroring MobixFL's branch visibility:
 * <ul>
 *   <li>{@code orgUnitIds == null} -- unrestricted: the whole company (holds org.scope.all).</li>
 *   <li>a non-empty set -- the user's own unit and every unit under it.</li>
 *   <li>an empty set -- no unit on the profile: only rows the user raised themselves.
 *       Fails closed, never "everything".</li>
 * </ul>
 * A row the user raised themselves is always in scope, whatever its unit.
 */
public record OrgScope(UUID userId, Set<UUID> orgUnitIds) {

    public boolean unrestricted() {
        return orgUnitIds == null;
    }

    /** Is a row stamped with {@code unit} and raised by {@code owner} visible to this user? */
    public boolean covers(OrgUnit unit, User owner) {
        if (unrestricted()) {
            return true;
        }
        if (owner != null && owner.getId().equals(userId)) {
            return true;
        }
        return unit != null && orgUnitIds.contains(unit.getId());
    }
}
