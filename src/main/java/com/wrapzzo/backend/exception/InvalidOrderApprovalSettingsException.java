package com.wrapzzo.backend.exception;

/** The order-approval setup asked for does not make sense (no approver picked, a role of another company). Answers 400. */
public class InvalidOrderApprovalSettingsException extends RuntimeException {

    public InvalidOrderApprovalSettingsException(String message) {
        super(message);
    }
}
