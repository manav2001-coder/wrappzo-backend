package com.wrapzzo.backend.exception;

import java.util.UUID;

public class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(UUID id) {
        super("Recipient not found: " + id);
    }
}
