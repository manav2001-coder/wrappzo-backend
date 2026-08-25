package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignSubmissionAttachmentRequest(@NotBlank String contentType) {
}
