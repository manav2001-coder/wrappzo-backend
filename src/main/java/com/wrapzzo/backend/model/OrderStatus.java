package com.wrapzzo.backend.model;

/**
 * PRD 2.11 -- the customer-facing lifecycle. No vendor/QC/production internals leak into this.
 * RECEIVED_AT_WAREHOUSE and OUT_FOR_DELIVERY apply only to an order routed through one of the
 * company's warehouses (Order.warehouse): the vendor dispatches to the warehouse, which receives
 * it and delivers it onward.
 */
public enum OrderStatus {
    PROCESSING,
    PRODUCTION_QUEUE,
    DISPATCHED,
    RECEIVED_AT_WAREHOUSE,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
