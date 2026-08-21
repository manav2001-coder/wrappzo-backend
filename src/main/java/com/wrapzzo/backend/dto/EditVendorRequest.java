package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record EditVendorRequest(@NotBlank String name, String contactPhone) {
}
