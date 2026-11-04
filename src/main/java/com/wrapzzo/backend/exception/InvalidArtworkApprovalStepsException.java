package com.wrapzzo.backend.exception;

/** The approval steps asked for do not make sense (a role that is not this company's, say). Answers 400. */
public class InvalidArtworkApprovalStepsException extends RuntimeException {

    public InvalidArtworkApprovalStepsException(String message) {
        super(message);
    }
}
