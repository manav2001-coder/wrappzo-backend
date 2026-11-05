package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Where one vendor serves inside the caller's company: the whole company, or only the org units named (a division, or any unit within one). */
public record SetVendorCoverageRequest(boolean wholeCompany, @NotNull @Size(max = 500) List<UUID> orgUnitIds) {
}
