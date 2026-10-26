package com.wrapzzo.backend.exception;

import java.util.UUID;

/** The order's gift needs approved artwork (Gift.artworkRequired) and none of its proofs is approved yet. */
public class ArtworkNotApprovedException extends RuntimeException {

    public ArtworkNotApprovedException(UUID orderId) {
        super("Order " + orderId + " can't be dispatched until its artwork is approved.");
    }
}
