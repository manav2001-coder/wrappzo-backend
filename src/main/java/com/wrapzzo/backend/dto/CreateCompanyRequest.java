package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(@NotBlank String name) {
}
