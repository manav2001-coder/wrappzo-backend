package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateQuickGiftRequest(
        @NotNull UUID giftId,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        String recipientEmail,
        @NotBlank String deliveryAddress,
        LocalDate recipientDob,
        String giftMessage,
        String internalNotes,
        @Valid QuickGiftPhotoInput photo) {

    /** References an already-presigned-and-uploaded photo (see presignPhoto) -- optional; a missing photo triggers a self-upload link to the recipient once approved. */
    public record QuickGiftPhotoInput(@NotBlank String key, @NotBlank String fileName, @NotBlank String contentType) {
    }
}
