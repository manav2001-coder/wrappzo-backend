package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachProfilePhotoRequest(@NotBlank String key) {
}
