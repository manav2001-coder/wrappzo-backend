package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectDoctorChangeRequest(@NotBlank String reason) {
}
