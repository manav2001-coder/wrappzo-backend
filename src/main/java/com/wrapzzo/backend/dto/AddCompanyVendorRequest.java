package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A company adds its own vendor: where it serves is the whole company, or exactly the org units named (a division, or any
 * unit within one). No markup or commission -- those are not the company's to set.
 */
public record AddCompanyVendorRequest(
        @NotBlank String name,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        boolean wholeCompany,
        @NotNull @Size(max = 500) List<UUID> orgUnitIds) {
}
