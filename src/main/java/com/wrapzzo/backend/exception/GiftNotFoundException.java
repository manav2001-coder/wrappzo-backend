package com.wrapzzo.backend.exception;

import java.util.UUID;

public class GiftNotFoundException extends RuntimeException {

    public GiftNotFoundException(UUID id) {
        super("Gift not found: " + id);
    }
}
