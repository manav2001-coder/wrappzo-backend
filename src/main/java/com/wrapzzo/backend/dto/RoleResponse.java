package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        /** Company Admin and Member: the app relies on them by name, so they can't be renamed, deactivated or removed. */
        boolean protectedRole,
        long userCount,
        Set<UUID> templateIds,
        Instant addedOn,
        String addedByName,
        Instant updatedOn,
        String updatedByName) {

    public static RoleResponse from(Role role, boolean protectedRole, long userCount) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                protectedRole,
                userCount,
                role.getTemplates().stream().map(Template::getId).collect(Collectors.toSet()),
                role.getCreatedAt(),
                role.getCreatedByName(),
                role.getUpdatedAt(),
                role.getUpdatedByName());
    }
}
