package com.wrapzzo.backend.model;

/**
 * The order lifecycle as far as the platform goes: it ends when the vendor dispatches the order (after the artwork is approved).
 * What happens after that -- the parcel reaching the doctor -- is not tracked here.
 */
public enum OrderStatus {
    PROCESSING,
    PRODUCTION_QUEUE,
    DISPATCHED,
    CANCELLED
}
