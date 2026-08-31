package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateGiftRequest(
        @NotBlank String name,
        @NotBlank String category,
        String description,
        @Positive Integer moq,
        @Positive Integer leadTimeDays,
        @NotNull @PositiveOrZero BigDecimal price,
        @PositiveOrZero BigDecimal markupFeePercentage,
        @PositiveOrZero BigDecimal commissionFeePercentage) {
}
