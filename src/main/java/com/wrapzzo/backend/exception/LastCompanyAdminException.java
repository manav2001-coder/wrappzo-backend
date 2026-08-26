package com.wrapzzo.backend.exception;

import java.util.UUID;

public class LastCompanyAdminException extends RuntimeException {

    public LastCompanyAdminException(UUID companyId) {
        super("Company " + companyId + " needs at least one Company Admin -- promote someone else first.");
    }
}
