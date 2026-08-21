package com.wrapzzo.backend.exception;

public class VendorEmailAlreadyExistsException extends RuntimeException {

    public VendorEmailAlreadyExistsException(String contactEmail) {
        super("A vendor with this email already exists: " + contactEmail);
    }
}
