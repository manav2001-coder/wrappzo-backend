package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Warehouse;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String address,
        String contactPhone,
        /** Null = serves the whole company (the fallback when no unit-specific warehouse matches). */
        UUID orgUnitId,
        String orgUnitName,
        boolean active) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getContactPhone(),
                warehouse.getOrgUnit() != null ? warehouse.getOrgUnit().getId() : null,
                warehouse.getOrgUnit() != null ? warehouse.getOrgUnit().getName() : null,
                warehouse.isActive());
    }
}
