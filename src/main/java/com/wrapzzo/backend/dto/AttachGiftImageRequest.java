package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachGiftImageRequest(@NotBlank String key, Integer displayOrder) {
}
