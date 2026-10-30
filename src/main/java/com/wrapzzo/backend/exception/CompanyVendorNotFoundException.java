package com.wrapzzo.backend.exception;

import java.util.UUID;

public class CompanyVendorNotFoundException extends RuntimeException {

    public CompanyVendorNotFoundException(UUID mappingId) {
        super("Vendor mapping not found: " + mappingId);
    }
}
