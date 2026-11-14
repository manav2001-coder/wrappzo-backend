package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.QuickGiftRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order waiting on the caller's yes. Never names the vendor: the ordering side does not see it. */
public record PendingOrderApprovalResponse(
        UUID orderId,
        String displayId,
        String giftName,
        String recipientName,
        String recipientPcode,
        int quantity,
        BigDecimal totalPrice,
        String requestedByName,
        String orgUnitName,
        String giftMessage,
        String internalNotes,
        /** The photos the field person attached to the request, so the approver can judge them (resolution, the right doctor) before deciding. Empty if none. */
        List<PhotoResponse> recipientPhotos,
        Instant requestedAt) {

    public static PendingOrderApprovalResponse from(Order order, QuickGiftRequest request, boolean showPrice, List<PhotoResponse> recipientPhotos) {
        return new PendingOrderApprovalResponse(
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                request != null ? request.getRecipientPcode() : null,
                order.getQuantity(),
                showPrice ? order.getTotalPrice() : null,
                order.getOwner().getName(),
                order.getOrgUnit() != null ? order.getOrgUnit().getName() : null,
                request != null ? request.getGiftMessage() : null,
                request != null ? request.getInternalNotes() : null,
                recipientPhotos,
                order.getCreatedAt());
    }
}
