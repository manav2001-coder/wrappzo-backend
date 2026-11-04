package com.wrapzzo.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

    @Test
    void theEmailIsTrimmedAndLowerCasedBeforeValidation() {
        LoginRequest request = new LoginRequest("  User@Email.COM \t", " keep my spaces ");

        assertThat(request.email()).isEqualTo("user@email.com");
        assertThat(request.password()).isEqualTo(" keep my spaces ");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void aMissingEmailStillFailsValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new LoginRequest(null, "x"))).isNotEmpty();
            assertThat(factory.getValidator().validate(new LoginRequest("   ", "x"))).isNotEmpty();
        }
    }
}
