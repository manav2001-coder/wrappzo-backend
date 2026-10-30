package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import java.util.UUID;

/** One entry of the feature master: a permission key, with whether a company can hold it and whether it is switched on. */
public record FeatureResponse(
        UUID id,
        String key,
        String module,
        String action,
        String name,
        String description,
        String category,
        boolean companyAssignable,
        boolean active,
        int sortOrder) {

    public static FeatureResponse from(AccessControl accessControl) {
        return new FeatureResponse(
                accessControl.getId(),
                accessControl.getKey(),
                accessControl.getModule(),
                accessControl.getAction(),
                accessControl.getName(),
                accessControl.getDescription(),
                accessControl.getCategory(),
                accessControl.isCompanyAssignable(),
                accessControl.isActive(),
                accessControl.getSortOrder());
    }
}
