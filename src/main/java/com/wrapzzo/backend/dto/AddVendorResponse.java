package com.wrapzzo.backend.dto;

/** temporaryPassword is only non-null when the caller left it out and we generated one -- share it manually, per the PRD. */
public record AddVendorResponse(VendorResponse vendor, String temporaryPassword) {
}
