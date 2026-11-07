package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/** What the warehouse's order queue can be searched, filtered and sorted by, and which orders belong in it. */
final class WarehouseOrderListFields {

    private WarehouseOrderListFields() {
    }

    static ListFields<Order> fields() {
        return new ListFields<Order>()
                .text("displayId", (root, cb) -> cb.concat("ORD-", root.get("orderNo").as(String.class)), "orderNo", true)
                .text("giftName", (root, cb) -> ListFields.join(root, "gift").get("name"), "gift.name", true)
                .text("recipientName", (root, cb) -> root.get("recipientName"), "recipientName", true)
                .text("warehouseName", (root, cb) -> ListFields.join(root, "warehouse").get("name"), "warehouse.name", true)
                .text("requestedByName", (root, cb) -> ListFields.join(root, "owner").get("name"), "owner.name", true)
                .text("orgUnitName", (root, cb) -> ListFields.join(root, "orgUnit").get("name"), "orgUnit.name", true)
                .number("quantity", (root, cb) -> root.get("quantity"), "quantity")
                .enumeration("status", OrderStatus.class, (root, cb) -> root.get("status"), "status")
                .date("updatedAt", (root, cb) -> root.get("updatedAt"), "updatedAt");
    }

    /** Orders routed through a warehouse and in the stages the queue covers. */
    static Specification<Order> inQueue(Collection<OrderStatus> statuses) {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("warehouse")), root.get("status").in(statuses));
    }
}
