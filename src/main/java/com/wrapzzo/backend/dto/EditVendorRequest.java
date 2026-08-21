package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record EditVendorRequest(
        @NotBlank String name,
        String contactPhone,
        @DecimalMin("0") @DecimalMax("100") BigDecimal markupFees,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commission) {
}
