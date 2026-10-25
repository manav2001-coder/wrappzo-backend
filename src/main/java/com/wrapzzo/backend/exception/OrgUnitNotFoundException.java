package com.wrapzzo.backend.exception;

import java.util.UUID;

public class OrgUnitNotFoundException extends RuntimeException {

    public OrgUnitNotFoundException(UUID id) {
        super("Org unit not found: " + id);
    }
}
