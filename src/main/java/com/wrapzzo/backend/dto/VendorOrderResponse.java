package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A vendor's own view of an order assigned to them -- deliberately narrower than OrderResponse:
 * no assignment metadata (it's already scoped to them), no ids they have no route to use, and
 * price excludes the markup fee entirely (that's Wrapzzo's own margin, not the vendor's business)
 * -- only the gift's base price plus their commission fee is shown.
 */
public record VendorOrderResponse(
        UUID id,
        String displayId,
        String companyName,
        String campaignName,
        String giftName,
        String recipientName,
        String recipientPhone,
        /** Gift price + commission fee only -- never includes markup. Null if the gift has no price set. */
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
                vendorPrice(order.getGift()),
                order.getSource(),
                order.getStatus(),
                order.getAssignedAt(),
                order.getCreatedAt());
    }

    private static BigDecimal vendorPrice(Gift gift) {
        if (gift == null || gift.getPrice() == null) {
            return null;
        }
        BigDecimal commissionAmount = gift.getPrice()
                .multiply(gift.getCommissionFeePercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return gift.getPrice().add(commissionAmount);
    }
}
