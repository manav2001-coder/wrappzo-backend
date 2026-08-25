package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CampaignFieldRequest(
        @NotBlank String fieldKey,
        @NotBlank String label,
        @NotNull FieldType fieldType,
        boolean required,
        String placeholder,
        List<String> options) {
}
