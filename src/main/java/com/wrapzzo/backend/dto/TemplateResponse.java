package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Template;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A template with what it grants itself ({@code accessControlIds}), what it includes, and the
 * full set once includes are followed ({@code effectiveAccessControlIds}). A null companyId is a
 * system master template.
 */
public record TemplateResponse(
        UUID id,
        UUID companyId,
        String name,
        String description,
        boolean active,
        boolean system,
        Set<UUID> accessControlIds,
        Set<UUID> referencedTemplateIds,
        Set<UUID> effectiveAccessControlIds,
        long roleCount,
        long userCount) {

    public static TemplateResponse from(Template template, Set<UUID> effectiveAccessControlIds, long roleCount, long userCount) {
        return new TemplateResponse(
                template.getId(),
                template.getCompany() != null ? template.getCompany().getId() : null,
                template.getName(),
                template.getDescription(),
                template.isActive(),
                template.isSystem(),
                template.getAccessControls().stream().map(ac -> ac.getId()).collect(Collectors.toSet()),
                template.getReferences().stream().map(Template::getId).collect(Collectors.toSet()),
                effectiveAccessControlIds,
                roleCount,
                userCount);
    }
}
