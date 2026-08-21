package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SetupVendorPasswordRequest(@NotBlank String token, @NotBlank String password, @NotBlank String confirmPassword) {
}
