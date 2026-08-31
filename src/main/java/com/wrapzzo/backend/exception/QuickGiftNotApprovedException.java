package com.wrapzzo.backend.exception;

import java.util.UUID;

public class QuickGiftNotApprovedException extends RuntimeException {

    public QuickGiftNotApprovedException(UUID id) {
        super("Quick Gift request " + id + " isn't approved yet -- there's no order to request a photo for");
    }
}
