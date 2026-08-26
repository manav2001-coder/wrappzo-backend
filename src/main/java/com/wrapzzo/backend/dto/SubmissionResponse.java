package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID campaignId,
        String campaignName,
        UUID submittedById,
        String submittedByName,
        UUID giftId,
        String giftName,
        SubmissionStatus status,
        String rejectionReason,
        String reviewedByName,
        Instant reviewedAt,
        Instant submittedAt,
        List<SubmissionValueDto> values,
        List<SubmissionAttachmentDto> attachments,
        Instant createdAt,
        /** Only non-null right after an approve() call that just issued a fresh recipient photo-upload link -- lets the Admin UI offer to share it (e.g. via WhatsApp) immediately, on top of the email that's already sent. */
        String recipientUploadUrl) {

    public static SubmissionResponse from(Submission submission, List<SubmissionValueDto> values, List<SubmissionAttachmentDto> attachments) {
        return from(submission, values, attachments, null);
    }

    public static SubmissionResponse from(
            Submission submission, List<SubmissionValueDto> values, List<SubmissionAttachmentDto> attachments, String recipientUploadUrl) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getCampaign().getId(),
                submission.getCampaign().getName(),
                submission.getSubmittedBy().getId(),
                submission.getSubmittedBy().getName(),
                submission.getGift() != null ? submission.getGift().getId() : null,
                submission.getGift() != null ? submission.getGift().getName() : null,
                submission.getStatus(),
                submission.getRejectionReason(),
                submission.getReviewedBy() != null ? submission.getReviewedBy().getName() : null,
                submission.getReviewedAt(),
                submission.getSubmittedAt(),
                values,
                attachments,
                submission.getCreatedAt(),
                recipientUploadUrl);
    }
}
