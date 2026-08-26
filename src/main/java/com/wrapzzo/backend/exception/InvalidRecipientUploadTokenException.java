package com.wrapzzo.backend.exception;

public class InvalidRecipientUploadTokenException extends RuntimeException {

    public InvalidRecipientUploadTokenException() {
        super("This upload link is invalid or has expired");
    }
}
