package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import java.util.UUID;

/** One grantable permission. category/name are the module's and action's labels; sortOrder lays out the access grid. */
public record AccessControlResponse(
        UUID id,
        String key,
        String module,
        String action,
        String name,
        String description,
        String category,
        AccessControlType type,
        int sortOrder) {

    public static AccessControlResponse from(AccessControl accessControl) {
        return new AccessControlResponse(
                accessControl.getId(),
                accessControl.getKey(),
                accessControl.getModule(),
                accessControl.getAction(),
                accessControl.getName(),
                accessControl.getDescription(),
                accessControl.getCategory(),
                accessControl.getType(),
                accessControl.getSortOrder());
    }
}
