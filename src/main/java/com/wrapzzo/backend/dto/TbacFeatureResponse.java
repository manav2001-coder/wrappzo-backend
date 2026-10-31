package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.CompanyAccessControl;
import java.time.Instant;
import java.util.UUID;

/**
 * A feature a company has registered (one row of its Feature Master). The id is the permission
 * key's id: a feature is that key, once per company that has registered it.
 */
public record TbacFeatureResponse(
        UUID id,
        String key,
        String moduleName,
        String actionName,
        String description,
        boolean active,
        boolean isSystem,
        String addedByName,
        Instant addedOn,
        String updatedByName,
        Instant updatedOn) {

    public static TbacFeatureResponse from(CompanyAccessControl row) {
        AccessControl feature = row.getAccessControl();
        return new TbacFeatureResponse(
                feature.getId(), feature.getKey(), feature.getCategory(), feature.getName(), feature.getDescription(),
                row.isActive(), true, row.getAddedByName(), row.getAddedOn(), row.getUpdatedByName(), row.getUpdatedOn());
    }
}
