package com.wrapzzo.backend.exception;

/** An order asked for a quantity outside the fixed pad counts (5, 10, 15, 20, 25, 30). */
public class InvalidOrderQuantityException extends RuntimeException {

    public InvalidOrderQuantityException(String message) {
        super(message);
    }
}
