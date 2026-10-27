package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.DoctorChangeAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * recipientId required for EDIT/REMOVE, ignored for ADD. The proposed_* fields are required
 * for ADD, optional-but-meaningful for EDIT (only fields actually being changed need
 * sending -- the service falls back to the recipient's current value for anything blank),
 * unused for REMOVE.
 */
public record CreateDoctorChangeRequest(
        @NotNull DoctorChangeAction action,
        UUID recipientId,
        String proposedName,
        String proposedPhone,
        String proposedEmail,
        String proposedAddress,
        LocalDate proposedDob,
        /** Print details; for EDIT, a blank field keeps the doctor's current value, like the fields above. */
        @Valid DoctorDetails proposedDoctor) {
}
