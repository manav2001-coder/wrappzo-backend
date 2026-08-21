package com.wrapzzo.backend.exception;

/** Thrown when the upstream email provider (Resend) rejects or fails to send a message. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
