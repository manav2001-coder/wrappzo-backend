package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneTemplateRequest(@NotBlank @Size(max = 200) String name) {
}
