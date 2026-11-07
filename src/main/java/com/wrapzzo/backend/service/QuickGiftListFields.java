package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** What "My Quick Gifts" can be searched, filtered and sorted by, and the scope: the caller's own requests. */
final class QuickGiftListFields {

    private QuickGiftListFields() {
    }

    static ListFields<QuickGiftRequest> fields() {
        return new ListFields<QuickGiftRequest>()
                .text("displayId", (root, cb) -> cb.concat("QG-", root.get("requestNo").as(String.class)), "requestNo", true)
                .text("giftName", (root, cb) -> ListFields.join(root, "gift").get("name"), "gift.name", true)
                .text("recipientName", (root, cb) -> root.get("recipientName"), "recipientName", true)
                .text("recipientPcode", (root, cb) -> root.get("recipientPcode"), "recipientPcode", true)
                .text("orderDisplayId", (root, cb) -> cb.concat("ORD-", ListFields.join(root, "order").get("orderNo").as(String.class)), "order.orderNo", true)
                .number("quantity", (root, cb) -> root.get("quantity"), "quantity")
                .enumeration("status", QuickGiftStatus.class, (root, cb) -> root.get("status"), "status")
                .date("createdAt", (root, cb) -> root.get("createdAt"), "createdAt");
    }

    static Specification<QuickGiftRequest> requestedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("requestedBy").get("id"), userId);
    }
}
