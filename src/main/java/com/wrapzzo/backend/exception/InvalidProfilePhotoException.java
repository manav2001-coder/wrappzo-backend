package com.wrapzzo.backend.exception;

/** The uploaded picture cannot be used; the message says why (wrong type, too large, not the person's own upload). */
public class InvalidProfilePhotoException extends RuntimeException {

    public InvalidProfilePhotoException(String message) {
        super(message);
    }
}
