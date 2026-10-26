package com.wrapzzo.backend.exception;

import java.util.UUID;

public class DoctorChangeRequestAlreadyReviewedException extends RuntimeException {

    public DoctorChangeRequestAlreadyReviewedException(UUID id) {
        super("This doctor change request has already been reviewed: " + id);
    }
}
