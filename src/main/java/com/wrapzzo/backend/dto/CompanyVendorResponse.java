package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.util.UUID;

public record CompanyVendorResponse(UUID vendorId, String vendorName, VendorStatus vendorStatus) {

    public static CompanyVendorResponse from(CompanyVendor companyVendor) {
        return new CompanyVendorResponse(
                companyVendor.getVendor().getId(), companyVendor.getVendor().getName(), companyVendor.getVendor().getStatus());
    }
}
