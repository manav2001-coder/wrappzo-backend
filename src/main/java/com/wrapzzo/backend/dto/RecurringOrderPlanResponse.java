package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.RecurringOrderPlan;
import com.wrapzzo.backend.model.RecurringPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringOrderPlanResponse(
        UUID id,
        UUID giftId,
        String giftName,
        String vendorName,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        int quantity,
        RecurringPlanStatus status,
        /** When the next order is placed -- meaningless once cancelled. */
        LocalDate nextRunOn,
        int runsCompleted,
        /** Why the last scheduled run failed; null when it went through. */
        String lastError,
        Instant createdAt) {

    public static RecurringOrderPlanResponse from(RecurringOrderPlan plan) {
        return new RecurringOrderPlanResponse(
                plan.getId(),
                plan.getGift().getId(),
                plan.getGift().getName(),
                plan.getGift().getVendor() != null ? plan.getGift().getVendor().getName() : null,
                plan.getRecipientName(),
                plan.getRecipientPhone(),
                plan.getDeliveryAddress(),
                plan.getQuantity(),
                plan.getStatus(),
                plan.getNextRunOn(),
                plan.getRunsCompleted(),
                plan.getLastError(),
                plan.getCreatedAt());
    }
}
