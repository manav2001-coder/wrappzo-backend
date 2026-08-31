package com.wrapzzo.backend.exception;

import java.util.UUID;

public class QuickGiftRequestNotFoundException extends RuntimeException {

    public QuickGiftRequestNotFoundException(UUID id) {
        super("Quick Gift request not found: " + id);
    }
}
