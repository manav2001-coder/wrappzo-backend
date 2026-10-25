package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.util.UUID;

/** orgUnitId/orgUnitName null = this mapping is company-wide. */
public record CompanyVendorResponse(
        UUID mappingId, UUID vendorId, String vendorName, VendorStatus vendorStatus, UUID orgUnitId, String orgUnitName) {

    public static CompanyVendorResponse from(CompanyVendor companyVendor) {
        return new CompanyVendorResponse(
                companyVendor.getId(),
                companyVendor.getVendor().getId(),
                companyVendor.getVendor().getName(),
                companyVendor.getVendor().getStatus(),
                companyVendor.getOrgUnit() != null ? companyVendor.getOrgUnit().getId() : null,
                companyVendor.getOrgUnit() != null ? companyVendor.getOrgUnit().getName() : null);
    }
}
