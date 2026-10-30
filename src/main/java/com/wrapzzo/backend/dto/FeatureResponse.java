package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import java.util.UUID;

/**
 * One entry of the feature master. In a company's view {@code registered} says whether the company
 * has the key and {@code active} whether it is switched on there; in the platform catalogue both
 * are simply true.
 */
public record FeatureResponse(
        UUID id,
        String key,
        String module,
        String action,
        String name,
        String description,
        String category,
        boolean companyAssignable,
        boolean registered,
        boolean active,
        int sortOrder) {

    public static FeatureResponse from(AccessControl accessControl, boolean registered, boolean active) {
        return new FeatureResponse(
                accessControl.getId(),
                accessControl.getKey(),
                accessControl.getModule(),
                accessControl.getAction(),
                accessControl.getName(),
                accessControl.getDescription(),
                accessControl.getCategory(),
                accessControl.isCompanyAssignable(),
                registered,
                active,
                accessControl.getSortOrder());
    }
}
