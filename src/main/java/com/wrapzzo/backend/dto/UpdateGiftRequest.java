package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateGiftRequest(
        @NotBlank String name, @NotBlank String category, String description, @Positive Integer moq, @Positive Integer leadTimeDays) {
}
