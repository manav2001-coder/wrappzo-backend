package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Onboard or edit a company -- name required, the rest of its profile optional. The id format
 * checks accept an empty value (a cleared field) and otherwise the real format.
 */
public record CompanyRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String contactPersonName,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = "^$|^[0-9+()\\- ]{7,20}$", message = "must be a phone number") String contactMobile,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "must be a valid GSTIN") String gstin,
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "must be a valid PAN") String pan,
        @Size(max = 60) String registrationNumber,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Pattern(regexp = "^$|^[0-9]{6}$", message = "must be a 6-digit pincode") String pincode,
        @Size(max = 2000) String notes) {

    public static CompanyRequest named(String name) {
        return new CompanyRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
