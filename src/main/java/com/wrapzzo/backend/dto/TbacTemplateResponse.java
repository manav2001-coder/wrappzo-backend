package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Template;
import java.time.Instant;
import java.util.UUID;

/**
 * An access template. {@code templateKey} is derived once from the owner and the name and never
 * re-derived on rename. A null {@code companyId} is a system template: shared by every company,
 * changed by platform staff only. {@code effectiveFeatureCount} is direct plus inherited, resolved
 * through the templates it includes.
 */
public record TbacTemplateResponse(
        UUID id,
        UUID companyId,
        String name,
        String templateKey,
        String description,
        String icon,
        String path,
        boolean active,
        boolean isSystem,
        int directFeatureCount,
        int effectiveFeatureCount,
        String addedByName,
        Instant addedOn,
        String updatedByName,
        Instant updatedOn) {

    public static TbacTemplateResponse from(Template template, int effectiveFeatureCount) {
        return new TbacTemplateResponse(
                template.getId(),
                template.getCompany() != null ? template.getCompany().getId() : null,
                template.getName(),
                template.getTemplateKey(),
                template.getDescription(),
                template.getIcon(),
                template.getPath(),
                template.isActive(),
                template.isSystem(),
                template.getAccessControls().size(),
                effectiveFeatureCount,
                template.getCreatedByName(),
                template.getCreatedAt(),
                template.getUpdatedByName(),
                template.getUpdatedAt());
    }
}
