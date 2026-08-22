package com.wrapzzo.backend.exception;

import java.util.UUID;

/** A Super Admin may view and delist a vendor-submitted gift, but never edit its content. */
public class VendorGiftEditNotAllowedException extends RuntimeException {

    public VendorGiftEditNotAllowedException(UUID giftId) {
        super("Gift " + giftId + " was submitted by a vendor -- its content can't be edited by an admin.");
    }
}
