package com.wrapzzo.backend.exception;

import java.util.UUID;

public class QuickGiftAlreadyReviewedException extends RuntimeException {

    public QuickGiftAlreadyReviewedException(UUID id) {
        super("This Quick PEL request has already been reviewed: " + id);
    }
}
