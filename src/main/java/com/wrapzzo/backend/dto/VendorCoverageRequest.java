package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Where a vendor serves: each company, and within it the whole company (no org units) or only those org units. */
public record VendorCoverageRequest(@NotNull @Size(max = 200) List<@Valid Entry> companies) {

    public record Entry(@NotNull UUID companyId, @Size(max = 500) List<UUID> orgUnitIds) {
    }
}
