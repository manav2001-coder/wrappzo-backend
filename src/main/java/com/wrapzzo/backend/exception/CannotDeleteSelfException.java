package com.wrapzzo.backend.exception;

public class CannotDeleteSelfException extends RuntimeException {

    public CannotDeleteSelfException() {
        super("You can't delete your own account.");
    }
}
