package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** No markupFees/commission here -- those stay platform-controlled (Wrapzzo's own margin), set later via /api/platform/vendors if needed. */
public record AddCompanyVendorRequest(@NotBlank String name, @NotBlank @Email String contactEmail, String contactPhone) {
}
