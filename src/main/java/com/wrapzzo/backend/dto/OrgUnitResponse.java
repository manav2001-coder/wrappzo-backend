package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrgUnit;
import java.util.UUID;

public record OrgUnitResponse(UUID id, String name, String levelName, UUID parentId) {

    public static OrgUnitResponse from(OrgUnit unit) {
        return new OrgUnitResponse(unit.getId(), unit.getName(), unit.getLevelName(), unit.getParent() != null ? unit.getParent().getId() : null);
    }
}
