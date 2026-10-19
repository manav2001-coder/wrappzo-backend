package com.wrapzzo.backend.exception;

import java.util.UUID;

public class OrderDesignAlreadyReviewedException extends RuntimeException {

    public OrderDesignAlreadyReviewedException(UUID id) {
        super("This design version has already been reviewed: " + id);
    }
}
