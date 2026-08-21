package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String displayId,
        String name,
        String contactEmail,
        String contactPhone,
        BigDecimal markupFees,
        BigDecimal commission,
        VendorStatus status,
        Instant createdAt) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                "VID-" + vendor.getVendorNo(),
                vendor.getName(),
                vendor.getContactEmail(),
                vendor.getContactPhone(),
                vendor.getMarkupFees(),
                vendor.getCommission(),
                vendor.getStatus(),
                vendor.getCreatedAt());
    }
}
