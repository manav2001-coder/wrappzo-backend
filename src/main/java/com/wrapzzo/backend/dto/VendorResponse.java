package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String displayId,
        String name,
        String contactEmail,
        String contactPhone,
        /** The company that added and runs this vendor; null for one that predates ownership. */
        String companyName,
        /** Everywhere it serves, one entry per mapping: "Company · Division" or "Company · Whole company". */
        List<String> serves,
        BigDecimal markupFees,
        BigDecimal commission,
        VendorStatus status,
        Instant createdAt,
        Instant addedOn,
        String addedByName,
        Instant updatedOn,
        String updatedByName) {

    public static VendorResponse from(Vendor vendor, List<String> serves) {
        return new VendorResponse(
                vendor.getId(),
                "VID-" + vendor.getVendorNo(),
                vendor.getName(),
                vendor.getContactEmail(),
                vendor.getContactPhone(),
                vendor.getCompany() != null ? vendor.getCompany().getName() : null,
                serves,
                vendor.getMarkupFees(),
                vendor.getCommission(),
                vendor.getStatus(),
                vendor.getCreatedAt(),
                vendor.getCreatedAt(),
                vendor.getCreatedByName(),
                vendor.getUpdatedAt(),
                vendor.getUpdatedByName());
    }
}
