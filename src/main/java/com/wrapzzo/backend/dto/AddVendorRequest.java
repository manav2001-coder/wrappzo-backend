package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** password/confirmPassword are both optional together -- omit both to have one auto-generated. */
public record AddVendorRequest(
        @NotBlank String name,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        String password,
        String confirmPassword) {
}
