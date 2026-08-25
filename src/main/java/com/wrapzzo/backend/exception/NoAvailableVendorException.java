package com.wrapzzo.backend.exception;

/** Auto-assign found no active vendor to route the order to -- Admin must assign one manually. */
public class NoAvailableVendorException extends RuntimeException {

    public NoAvailableVendorException() {
        super("No active vendor is available to auto-assign -- assign one manually.");
    }
}
