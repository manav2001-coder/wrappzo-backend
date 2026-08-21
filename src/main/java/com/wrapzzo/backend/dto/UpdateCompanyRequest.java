package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCompanyRequest(@NotBlank String name) {
}
