package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** No password here -- the vendor sets their own via the emailed invite link. */
public record AddVendorRequest(
        @NotBlank String name,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        @DecimalMin("0") @DecimalMax("100") BigDecimal markupFees,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commission) {
}
