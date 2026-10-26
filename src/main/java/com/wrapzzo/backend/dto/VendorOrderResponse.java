package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
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
        /** Null until the recipient uploads one -- shown so the vendor can produce a personalized gift correctly. */
        String recipientPhotoUrl,
        /** The gift's raw price -- no markup, no commission. Null if the gift has no price set. */
        BigDecimal price,
        int quantity,
        /** What to print and where to ship -- a Quick Gift order's own request; null for a campaign order. */
        String deliveryAddress,
        String giftMessage,
        DoctorDetails doctor,
        /** True when this order may not be dispatched until a proof is approved (Gift.artworkRequired). */
        boolean artworkRequired,
        boolean artworkApproved,
        OrderSource source,
        OrderStatus status,
        Instant assignedAt,
        Instant createdAt) {

    /** {@code request} is the Quick Gift request the order came from, or null (a campaign order). */
    public static VendorOrderResponse from(Order order, String recipientPhotoUrl, QuickGiftRequest request, boolean artworkApproved) {
        return new VendorOrderResponse(
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getCompany().getName(),
                order.getCampaign() != null ? order.getCampaign().getName() : null,
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                order.getRecipientPhone(),
                recipientPhotoUrl,
                order.getGift() != null ? order.getGift().getPrice() : null,
                order.getQuantity(),
                request != null ? request.getDeliveryAddress() : null,
                request != null ? request.getGiftMessage() : null,
                request != null
                        ? new DoctorDetails(request.getRecipientQualification(), request.getRecipientRegistrationNumber(),
                                request.getRecipientSpeciality(), request.getRecipientClinicName())
                        : null,
                order.getGift() != null && order.getGift().isArtworkRequired(),
                artworkApproved,
                order.getSource(),
                order.getStatus(),
                order.getAssignedAt(),
                order.getCreatedAt());
    }
}
