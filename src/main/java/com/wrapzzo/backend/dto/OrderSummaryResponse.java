package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderStatus;
import java.util.Map;

/** The figures above an Orders list, counted in the database over the same orders the list covers -- not from a page of them. */
public record OrderSummaryResponse(
        long total,
        Map<OrderStatus, Long> byStatus,
        /** Orders with a proof waiting on a reviewer (company side). */
        long awaitingReview,
        /** Live orders not with a vendor yet and not held for approval (platform side). */
        long unassigned) {
}
