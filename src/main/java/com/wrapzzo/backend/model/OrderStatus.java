package com.wrapzzo.backend.model;

/** PRD 2.11 -- the customer-facing lifecycle. No vendor/QC/production internals leak into this. */
public enum OrderStatus {
    PROCESSING,
    PRODUCTION_QUEUE,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
