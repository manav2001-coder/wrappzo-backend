package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignLogoUploadRequest(
        @NotBlank @Pattern(regexp = "image/(png|jpeg|jpg|webp|svg\\+xml)", message = "must be an image content type")
                String contentType) {
}
