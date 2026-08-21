package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(UUID id, String name, String contactEmail, String contactPhone, VendorStatus status, Instant createdAt) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(), vendor.getName(), vendor.getContactEmail(), vendor.getContactPhone(), vendor.getStatus(), vendor.getCreatedAt());
    }
}
