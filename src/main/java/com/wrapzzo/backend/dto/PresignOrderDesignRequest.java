package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignOrderDesignRequest(@NotBlank String contentType) {
}
