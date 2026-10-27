package com.wrapzzo.backend.exception;

public class QuantityBelowMinimumException extends RuntimeException {

    public QuantityBelowMinimumException(String giftName, int moq, int quantity) {
        super("The minimum order for " + giftName + " is " + moq + " (asked for " + quantity + ")");
    }
}
