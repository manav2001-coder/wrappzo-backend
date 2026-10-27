package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * accessControlIds: the keys granted to the designation itself -- the editable part.
 * roles: what the people holding this designation already get from their role(s), shown
 * read-only beside it, so a designation with no extras doesn't look like "no access at all".
 */
public record DesignationAccessResponse(String designation, Set<UUID> accessControlIds, List<RoleGrant> roles) {

    /** One role held by users with this designation, how many of them hold it, and what it grants. */
    public record RoleGrant(String roleName, int userCount, Set<UUID> accessControlIds) {
    }
}
