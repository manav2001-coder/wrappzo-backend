package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QuickGiftRequestResponse(
        UUID id,
        String displayId,
        /** Null until approved. */
        UUID orderId,
        String orderDisplayId,
        UUID giftId,
        String giftName,
        String giftCategory,
        String recipientName,
        String recipientPhone,
        String recipientEmail,
        String deliveryAddress,
        LocalDate recipientDob,
        String giftMessage,
        String internalNotes,
        String photoUrl,
        String photoFileName,
        QuickGiftStatus status,
        String rejectionReason,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        /** Only set right after an approve() call that just issued a fresh recipient photo-upload link. */
        String recipientUploadUrl) {

    public static QuickGiftRequestResponse from(QuickGiftRequest request, String photoUrl) {
        return from(request, photoUrl, null);
    }

    public static QuickGiftRequestResponse from(QuickGiftRequest request, String photoUrl, String recipientUploadUrl) {
        Order order = request.getOrder();
        return new QuickGiftRequestResponse(
                request.getId(),
                "QG-" + request.getRequestNo(),
                order != null ? order.getId() : null,
                order != null ? "ORD-" + order.getOrderNo() : null,
                request.getGift().getId(),
                request.getGift().getName(),
                request.getGift().getCategory(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getRecipientEmail(),
                request.getDeliveryAddress(),
                request.getRecipientDob(),
                request.getGiftMessage(),
                request.getInternalNotes(),
                photoUrl,
                request.getPhotoFileName(),
                request.getStatus(),
                request.getRejectionReason(),
                request.getReviewedBy() != null ? request.getReviewedBy().getName() : null,
                request.getReviewedAt(),
                request.getCreatedAt(),
                recipientUploadUrl);
    }
}
