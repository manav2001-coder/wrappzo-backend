package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.GiftModule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record CreateGiftRequest(
        @NotBlank String name,
        @NotBlank String category,
        String description,
        @Positive Integer moq,
        @Positive Integer leadTimeDays,
        Set<GiftModule> modules) {
}
