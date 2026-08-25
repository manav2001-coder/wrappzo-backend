package com.wrapzzo.backend.exception;

import java.util.UUID;

/** PRD 2.7 -- approval must respect the company hierarchy: the submitter's manager, or campaign.manage. */
public class NotAuthorizedToReviewException extends RuntimeException {

    public NotAuthorizedToReviewException(UUID submissionId) {
        super("You are not authorized to review this submission: " + submissionId);
    }
}
