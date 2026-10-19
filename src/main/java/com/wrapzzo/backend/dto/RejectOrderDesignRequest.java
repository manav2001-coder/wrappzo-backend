package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectOrderDesignRequest(@NotBlank String comment) {
}
