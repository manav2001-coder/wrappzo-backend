package com.wrapzzo.backend.exception;

import java.util.UUID;

public class GiftImageNotFoundException extends RuntimeException {

    public GiftImageNotFoundException(UUID id) {
        super("Gift image not found: " + id);
    }
}
