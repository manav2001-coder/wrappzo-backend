package com.wrapzzo.backend.exception;

import java.util.UUID;

public class OrderDesignNotFoundException extends RuntimeException {

    public OrderDesignNotFoundException(UUID id) {
        super("Order design not found: " + id);
    }
}
