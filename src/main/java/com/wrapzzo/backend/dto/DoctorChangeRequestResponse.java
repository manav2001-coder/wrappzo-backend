package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.DoctorChangeAction;
import com.wrapzzo.backend.model.DoctorChangeRequest;
import com.wrapzzo.backend.model.DoctorChangeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DoctorChangeRequestResponse(
        UUID id,
        DoctorChangeAction action,
        UUID recipientId,
        String recipientName,
        String proposedName,
        String proposedPhone,
        String proposedEmail,
        String proposedAddress,
        LocalDate proposedDob,
        DoctorDetails proposedDoctor,
        String requestedByName,
        DoctorChangeStatus status,
        String reviewedByName,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt) {

    public static DoctorChangeRequestResponse from(DoctorChangeRequest r) {
        return new DoctorChangeRequestResponse(
                r.getId(),
                r.getAction(),
                r.getRecipient() != null ? r.getRecipient().getId() : null,
                r.getRecipient() != null ? r.getRecipient().getName() : null,
                r.getProposedName(),
                r.getProposedPhone(),
                r.getProposedEmail(),
                r.getProposedAddress(),
                r.getProposedDob(),
                new DoctorDetails(r.getProposedQualification(), r.getProposedRegistrationNumber(), r.getProposedSpeciality(),
                        r.getProposedClinicName()),
                r.getRequestedBy().getName(),
                r.getStatus(),
                r.getReviewedBy() != null ? r.getReviewedBy().getName() : null,
                r.getReviewedAt(),
                r.getRejectionReason(),
                r.getCreatedAt());
    }
}
