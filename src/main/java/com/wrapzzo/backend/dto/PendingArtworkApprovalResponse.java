package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.User;
import java.time.Instant;
import java.util.UUID;

/** An MR-approved proof waiting for a senior's final approval -- one row of their Approvals queue. */
public record PendingArtworkApprovalResponse(
        UUID designId,
        UUID orderId,
        String orderDisplayId,
        String giftName,
        String recipientName,
        int quantity,
        String requestedByName,
        int versionNo,
        String fileUrl,
        String fileName,
        String vendorName,
        String mrApprovedByName,
        Instant mrApprovedAt) {

    public static PendingArtworkApprovalResponse from(OrderDesign design, String fileUrl, User requester) {
        Order order = design.getOrder();
        return new PendingArtworkApprovalResponse(
                design.getId(),
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                order.getQuantity(),
                requester != null ? requester.getName() : null,
                design.getVersionNo(),
                fileUrl,
                design.getFileName(),
                design.getUploadedBy().getName(),
                design.getFirstApprovedBy() != null ? design.getFirstApprovedBy().getName() : null,
                design.getFirstApprovedAt());
    }
}
