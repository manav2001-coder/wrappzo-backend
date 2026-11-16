package com.wrapzzo.backend.exception;

/** A new password that does not meet the rules; the message says which rule, so the person knows what to change. */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
