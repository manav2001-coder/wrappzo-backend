package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectSubmissionRequest(@NotBlank String reason) {
}
