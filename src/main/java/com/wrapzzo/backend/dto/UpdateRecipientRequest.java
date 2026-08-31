package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateRecipientRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        String address,
        LocalDate dateOfBirth) {
}
