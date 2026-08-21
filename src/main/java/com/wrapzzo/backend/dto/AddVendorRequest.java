package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** No password here -- the vendor sets their own via the emailed invite link. */
public record AddVendorRequest(@NotBlank String name, @NotBlank @Email String contactEmail, String contactPhone) {
}
