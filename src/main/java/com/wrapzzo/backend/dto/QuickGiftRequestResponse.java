package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        String requestedByName,
        String recipientName,
        String recipientPhone,
        /** The doctor's pcode when ordered. */
        String recipientPcode,
        String recipientEmail,
        String deliveryAddress,
        LocalDate recipientDob,
        DoctorDetails doctor,
        int quantity,
        String orgUnitName,
        String giftMessage,
        String internalNotes,
        /** The first photo, for screens that show one; {@code photos} has them all. */
        String photoUrl,
        String photoFileName,
        List<PhotoResponse> photos,
        QuickGiftStatus status,
        String rejectionReason,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        /** Only set right after an approve() call that just issued a fresh recipient photo-upload link. */
        String recipientUploadUrl,
        /** Whether the order is held for its approver (PENDING) or went straight to production; null before an order exists. */
        OrderApprovalStatus orderApprovalStatus) {

    public static QuickGiftRequestResponse from(QuickGiftRequest request, List<PhotoResponse> photos) {
        return from(request, photos, null);
    }

    public static QuickGiftRequestResponse from(QuickGiftRequest request, List<PhotoResponse> photos, String recipientUploadUrl) {
        Order order = request.getOrder();
        return new QuickGiftRequestResponse(
                request.getId(),
                "QG-" + request.getRequestNo(),
                order != null ? order.getId() : null,
                order != null ? "ORD-" + order.getOrderNo() : null,
                request.getGift().getId(),
                request.getGift().getName(),
                request.getGift().getCategory(),
                request.getRequestedBy() != null ? request.getRequestedBy().getName() : null,
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getRecipientPcode(),
                request.getRecipientEmail(),
                request.getDeliveryAddress(),
                request.getRecipientDob(),
                new DoctorDetails(request.getRecipientQualification(), request.getRecipientSpeciality(), request.getRecipientClinicName()),
                request.getQuantity(),
                request.getOrgUnit() != null ? request.getOrgUnit().getName() : null,
                request.getGiftMessage(),
                request.getInternalNotes(),
                photos.isEmpty() ? null : photos.get(0).url(),
                photos.isEmpty() ? null : photos.get(0).fileName(),
                photos,
                request.getStatus(),
                request.getRejectionReason(),
                request.getReviewedBy() != null ? request.getReviewedBy().getName() : null,
                request.getReviewedAt(),
                request.getCreatedAt(),
                recipientUploadUrl,
                order != null ? order.getApprovalStatus() : null);
    }
}
