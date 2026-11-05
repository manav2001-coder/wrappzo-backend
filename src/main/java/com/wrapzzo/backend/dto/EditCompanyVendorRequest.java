package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record EditCompanyVendorRequest(@NotBlank String name, String contactPhone) {
}
