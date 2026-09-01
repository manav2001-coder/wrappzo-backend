package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.dto.DashboardResponse.StageEvent;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderAssignmentMode;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        /** The gift's first catalogue image, if it has one -- same lookup as the Dashboard's order timeline chips. */
        String giftImageUrl,
        String recipientName,
        String recipientPhone,
        /** Null until the recipient (or someone on their behalf) uploads one -- see OrderPhotoService. */
        String recipientPhotoUrl,
        /** Snapshotted off the gift's total price at approval time -- see Order.totalPrice. */
        BigDecimal totalPrice,
        OrderSource source,
        OrderStatus status,
        UUID assignedVendorId,
        String assignedVendorName,
        OrderAssignmentMode assignmentMode,
        Instant assignedAt,
        Instant createdAt,
        /** Every stage this order has actually passed through, oldest first -- same shape as the Dashboard's order timeline. */
        List<StageEvent> history) {

    public static OrderResponse from(Order order, String recipientPhotoUrl, String giftImageUrl, List<StageEvent> history) {
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
                giftImageUrl,
                order.getRecipientName(),
                order.getRecipientPhone(),
                recipientPhotoUrl,
                order.getTotalPrice(),
                order.getSource(),
                order.getStatus(),
                order.getAssignedVendor() != null ? order.getAssignedVendor().getId() : null,
                order.getAssignedVendor() != null ? order.getAssignedVendor().getName() : null,
                order.getAssignmentMode(),
                order.getAssignedAt(),
                order.getCreatedAt(),
                history);
    }
}
