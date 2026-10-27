package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** orgUnitId null = the company-wide fallback warehouse; active null on create means active. */
public record WarehouseRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2000) String address,
        @Size(max = 50) String contactPhone,
        UUID orgUnitId,
        Boolean active) {
}
