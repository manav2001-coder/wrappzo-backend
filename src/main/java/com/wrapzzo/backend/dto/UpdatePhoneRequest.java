package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
        @NotBlank(message = "Enter a phone number")
        @Pattern(regexp = "^[+0-9][0-9 ()-]{6,19}$", message = "Enter a valid phone number: digits, with an optional + at the start, 7 to 20 characters")
        String phone) {
}
