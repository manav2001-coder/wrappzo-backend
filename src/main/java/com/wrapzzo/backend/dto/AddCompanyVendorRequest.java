package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * No markupFees/commission here -- those stay platform-controlled (Wrapzzo's own margin),
 * set later via /api/platform/vendors if needed. orgUnitId null = this vendor serves the
 * whole company; set = scoped to that org unit and everything beneath it.
 */
public record AddCompanyVendorRequest(@NotBlank String name, @NotBlank @Email String contactEmail, String contactPhone, UUID orgUnitId) {
}
