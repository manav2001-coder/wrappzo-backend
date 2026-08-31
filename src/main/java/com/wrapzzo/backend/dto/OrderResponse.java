package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderAssignmentMode;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String displayId,
        UUID companyId,
        String companyName,
        /** Null for a Quick Gift order (source != CAMPAIGN). */
        UUID campaignId,
        String campaignName,
        UUID submissionId,
        UUID giftId,
        String giftName,
        String recipientName,
        String recipientPhone,
        OrderSource source,
        OrderStatus status,
        UUID assignedVendorId,
        String assignedVendorName,
        OrderAssignmentMode assignmentMode,
        Instant assignedAt,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getCompany().getId(),
                order.getCompany().getName(),
                order.getCampaign() != null ? order.getCampaign().getId() : null,
                order.getCampaign() != null ? order.getCampaign().getName() : null,
                order.getSubmission() != null ? order.getSubmission().getId() : null,
                order.getGift() != null ? order.getGift().getId() : null,
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getSource(),
                order.getStatus(),
                order.getAssignedVendor() != null ? order.getAssignedVendor().getId() : null,
                order.getAssignedVendor() != null ? order.getAssignedVendor().getName() : null,
                order.getAssignmentMode(),
                order.getAssignedAt(),
                order.getCreatedAt());
    }
}
