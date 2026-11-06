package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** {@code number} is which photo of the order this is (1 when left out), so several do not share a name. {@code pcode} is the doctor's, so the stored file can be named after them; the server names it, never the client. */
public record PresignQuickGiftPhotoRequest(@NotBlank String contentType, String pcode, @Min(1) @Max(10) Integer number) {
}
