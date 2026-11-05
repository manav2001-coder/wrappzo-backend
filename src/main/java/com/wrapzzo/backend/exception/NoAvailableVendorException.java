package com.wrapzzo.backend.exception;

import com.wrapzzo.backend.model.OrgUnit;

public class NoAvailableVendorException extends RuntimeException {

    public NoAvailableVendorException() {
        super("No active vendor is available to auto-assign -- assign one manually.");
    }

    /** No active vendor is mapped to the order's unit, its division or the whole company. */
    public NoAvailableVendorException(OrgUnit unit) {
        super(unit == null
                ? "No active vendor is available to auto-assign -- assign one manually."
                : "No active vendor serves " + unit.getName() + " or its division -- map a vendor to it under Vendors, or assign one manually.");
    }
}
