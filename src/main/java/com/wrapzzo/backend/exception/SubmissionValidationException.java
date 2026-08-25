package com.wrapzzo.backend.exception;

import java.util.List;
import lombok.Getter;

/** Mirrors UserImportValidationException's shape -- row-level errors surfaced as a list, not a single message. */
@Getter
public class SubmissionValidationException extends RuntimeException {

    private final List<String> errors;

    public SubmissionValidationException(List<String> errors) {
        super("Submission validation failed.");
        this.errors = errors;
    }
}
