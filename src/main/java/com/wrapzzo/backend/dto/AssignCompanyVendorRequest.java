package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** orgUnitId null = company-wide; set = scoped to that org unit and everything beneath it. */
public record AssignCompanyVendorRequest(@NotNull UUID vendorId, UUID orgUnitId) {
}
