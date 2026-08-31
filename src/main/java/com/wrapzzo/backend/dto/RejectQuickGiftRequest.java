package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectQuickGiftRequest(@NotBlank String reason) {
}
