package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * recipientId set = an existing doctor picked from the shared directory; recipientName/
 * phone/etc are ignored server-side in favor of that record's own data. recipientId null =
 * a new doctor, described entirely by the free-text fields below (same as before).
 */
public record CreateQuickGiftRequest(
        @NotNull UUID giftId,
        UUID recipientId,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        /** A new doctor's pcode -- required when recipientId is null; ignored when an existing doctor is picked. */
        @Size(max = 50) String recipientPcode,
        String recipientEmail,
        @NotBlank String deliveryAddress,
        LocalDate recipientDob,
        String giftMessage,
        String internalNotes,
        /** The recipient's photos (up to 10), each already presigned and uploaded -- optional; with none, a self-upload link goes to the recipient. */
        @Size(max = 10, message = "An order can carry at most 10 photos.") List<@Valid QuickGiftPhotoInput> photos,
        /** Print details for a new doctor; ignored (the directory's own are used) when recipientId is set. */
        @Valid DoctorDetails doctor,
        /** Units to order -- pads, say. Null means 1. */
        @Min(1) @Max(100000) Integer quantity) {

    /** References an already-presigned-and-uploaded photo (see presignPhoto) -- optional; a missing photo triggers a self-upload link to the recipient once approved. */
    public record QuickGiftPhotoInput(@NotBlank String key, @NotBlank String fileName, @NotBlank String contentType) {
    }
}
