package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
import java.time.Instant;
import java.util.UUID;

/** An order on its way through one of the company's warehouses -- what the warehouse needs to receive it and deliver it onward. */
public record WarehouseOrderResponse(
        UUID id,
        String displayId,
        UUID warehouseId,
        String warehouseName,
        String giftName,
        int quantity,
        String recipientName,
        String recipientPhone,
        /** The final drop -- a Quick Gift order's own request; null for a campaign order. */
        String deliveryAddress,
        String vendorName,
        String orgUnitName,
        String requestedByName,
        OrderStatus status,
        Instant updatedAt) {

    public static WarehouseOrderResponse from(Order order, QuickGiftRequest request) {
        return new WarehouseOrderResponse(
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getWarehouse().getId(),
                order.getWarehouse().getName(),
                order.getGift() != null ? order.getGift().getName() : null,
                order.getQuantity(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                request != null ? request.getDeliveryAddress() : null,
                order.getAssignedVendor() != null ? order.getAssignedVendor().getName() : null,
                order.getOrgUnit() != null ? order.getOrgUnit().getName() : null,
                order.getCreatedBy() != null ? order.getCreatedBy().getName() : null,
                order.getStatus(),
                order.getUpdatedAt());
    }
}
