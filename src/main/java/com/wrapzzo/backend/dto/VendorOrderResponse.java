package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A vendor's own view of an order assigned to them -- deliberately narrower than OrderResponse:
 * no assignment metadata (it's already scoped to them), no ids they have no route to use, and
 * price is the gift's raw base price only -- neither the markup fee (Wrapzzo's own margin) nor
 * the commission fee is shown to the vendor.
 */
public record VendorOrderResponse(
        UUID id,
        String displayId,
        String companyName,
        String campaignName,
        String giftName,
        String recipientName,
        String recipientPhone,
        /** The gift's raw price -- no markup, no commission. Null if the gift has no price set. */
        BigDecimal price,
        OrderSource source,
        OrderStatus status,
        Instant assignedAt,
        Instant createdAt) {

    public static VendorOrderResponse from(Order order) {
        return new VendorOrderResponse(
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getCompany().getName(),
                order.getCampaign() != null ? order.getCampaign().getName() : null,
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getGift() != null ? order.getGift().getPrice() : null,
                order.getSource(),
                order.getStatus(),
                order.getAssignedAt(),
                order.getCreatedAt());
    }
}
