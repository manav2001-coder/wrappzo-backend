package com.wrapzzo.backend.exception;

import com.wrapzzo.backend.model.OrderStatus;
import java.util.UUID;

/** A vendor may only move an order forward one fulfillment step at a time -- see OrderService.VENDOR_TRANSITIONS. */
public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Order " + orderId + " can't move from " + from + " to " + to + ".");
    }
}
