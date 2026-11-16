package com.wrapzzo.backend.exception;

public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("This reset link is invalid, expired, or already used. Ask for a new one");
    }
}
