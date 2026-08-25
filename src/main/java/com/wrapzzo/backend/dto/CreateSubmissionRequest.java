package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * One answer per campaign field, keyed by fieldKey. TEXT/TEXTAREA/EMAIL/PHONE/SELECT/
 * RADIO/DATE values are plain strings; NUMBER a number; BOOLEAN/CHECKBOX a boolean;
 * MULTISELECT a list of strings; IMAGE/FILE a {"key","fileName","mimeType","fileSize"}
 * object referencing an already-presigned-and-uploaded file (see presignAttachment).
 * giftId is which of the campaign's offered gifts this recipient gets -- mandatory,
 * validated against the campaign's own gift list, not a generic form field.
 */
public record CreateSubmissionRequest(@NotNull UUID giftId, @NotNull Map<String, Object> values) {
}
