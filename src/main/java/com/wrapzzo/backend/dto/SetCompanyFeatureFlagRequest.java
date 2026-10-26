package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CompanyFeature;
import jakarta.validation.constraints.NotNull;

public record SetCompanyFeatureFlagRequest(@NotNull CompanyFeature flagKey, @NotNull Boolean enabled) {
}
