package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

    /**
     * Stray spaces and capital letters in the email are not a different person: trimmed and lower-cased
     * here, before validation runs, so " User@Email.com " signs in as user@email.com. The password is
     * left exactly as typed.
     */
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
