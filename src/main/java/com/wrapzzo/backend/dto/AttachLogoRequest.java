package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachLogoRequest(@NotBlank String key) {
}
