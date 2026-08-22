package com.wrapzzo.backend.exception;

import java.util.List;

/** All rows are pre-validated before any write happens -- either the whole sheet imports, or none of it does. */
public class UserImportValidationException extends RuntimeException {

    private final List<String> errors;

    public UserImportValidationException(List<String> errors) {
        super("Bulk import validation failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
