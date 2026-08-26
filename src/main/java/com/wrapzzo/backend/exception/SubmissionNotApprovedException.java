package com.wrapzzo.backend.exception;

import java.util.UUID;

public class SubmissionNotApprovedException extends RuntimeException {

    public SubmissionNotApprovedException(UUID id) {
        super("Submission " + id + " isn't approved yet -- there's no order to request a photo for");
    }
}
