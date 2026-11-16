package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignProfilePhotoRequest(@NotBlank String contentType) {
}
