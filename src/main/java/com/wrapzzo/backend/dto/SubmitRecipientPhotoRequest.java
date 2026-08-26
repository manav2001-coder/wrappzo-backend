package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** References an already-uploaded (presigned) file, same convention as a submission's own IMAGE/FILE field values. */
public record SubmitRecipientPhotoRequest(
        @NotBlank String key, @NotBlank String fileName, @NotBlank String mimeType, @NotNull Long fileSize) {
}
