package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignQuickGiftPhotoRequest(@NotBlank String contentType) {
}
