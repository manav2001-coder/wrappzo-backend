package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

/** Where a vendor serves. A company with no org units is served in whole. */
public record VendorCoverageResponse(List<CompanyCoverage> companies) {

    public record CompanyCoverage(UUID companyId, String companyName, List<UUID> orgUnitIds) {
    }
}
