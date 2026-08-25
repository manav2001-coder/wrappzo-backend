package com.wrapzzo.backend.exception;

import java.util.UUID;

/** Belt-and-braces guard alongside the DB-level unique constraint on orders.submission_id (PRD 4.6). */
public class SubmissionAlreadyReviewedException extends RuntimeException {

    public SubmissionAlreadyReviewedException(UUID submissionId) {
        super("This submission has already been reviewed: " + submissionId);
    }
}
