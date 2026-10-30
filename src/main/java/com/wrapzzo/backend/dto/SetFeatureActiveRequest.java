package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;

public record SetFeatureActiveRequest(@NotNull Boolean active) {
}
