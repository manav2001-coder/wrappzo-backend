package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import java.util.UUID;

public record AccessControlResponse(UUID id, String key, String name, String description, String category, AccessControlType type) {

    public static AccessControlResponse from(AccessControl accessControl) {
        return new AccessControlResponse(
                accessControl.getId(),
                accessControl.getKey(),
                accessControl.getName(),
                accessControl.getDescription(),
                accessControl.getCategory(),
                accessControl.getType());
    }
}
