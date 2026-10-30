package com.wrapzzo.backend.exception;

/** The request is fine but clashes with current state: a name already taken, or something still in use. Answers 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
