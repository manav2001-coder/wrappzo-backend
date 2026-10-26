package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CompanyFeature;

public record CompanyFeatureFlagResponse(CompanyFeature flagKey, String displayName, String description, boolean enabled) {

    public static CompanyFeatureFlagResponse of(CompanyFeature flag, boolean enabled) {
        return new CompanyFeatureFlagResponse(flag, flag.displayName(), flag.description(), enabled);
    }
}
