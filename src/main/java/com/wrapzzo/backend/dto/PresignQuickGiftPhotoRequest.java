package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code pcode} is the doctor's, so the stored file can be named after them; the server names it, never the client. */
public record PresignQuickGiftPhotoRequest(@NotBlank String contentType, String pcode) {
}
