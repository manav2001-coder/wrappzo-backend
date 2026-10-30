package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An MR-approved proof waiting on a manager's approval at its current level -- one row of their Approvals queue. */
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
        Instant mrApprovedAt,
        int approvalLevel,
        int approvalLevels,
        /** The manager this level waits on; null when no one is above the MR. */
        String waitingOnName,
        /** True when the caller isn't that manager and would sign as cover (order.design.final). */
        boolean covering,
        /** The decisions so far, oldest first. */
        List<ArtworkApprovalStep> approvals) {

    public static PendingArtworkApprovalResponse from(OrderDesign design, String fileUrl, User requester, User waitingOn, boolean covering,
            List<ArtworkApprovalStep> approvals) {
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
                design.getFirstApprovedAt(),
                design.getApprovalLevel() != null ? design.getApprovalLevel() : 1,
                design.getApprovalLevels() != null ? design.getApprovalLevels() : 1,
                waitingOn != null ? waitingOn.getName() : null,
                covering,
                approvals);
    }
}
