package com.wrapzzo.backend.exception;

public class InvalidAccessControlSelectionException extends RuntimeException {

    public InvalidAccessControlSelectionException() {
        super("One or more selected access-control keys don't exist.");
    }

    public InvalidAccessControlSelectionException(String message) {
        super(message);
    }
}
