package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code fileName} is the name of the file the vendor picked: it must follow PCode_DoctorName_AMName.pdf, or the upload is refused. */
public record PresignOrderDesignRequest(@NotBlank String contentType, @NotBlank @Size(max = 255) String fileName) {
}
