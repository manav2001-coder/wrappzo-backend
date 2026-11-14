package com.wrapzzo.backend.exception;

import java.util.UUID;

public class GiftNotAvailableForQuickGiftException extends RuntimeException {

    public GiftNotAvailableForQuickGiftException(UUID giftId) {
        super("This PEL isn't available for Quick PEL: " + giftId);
    }
}
