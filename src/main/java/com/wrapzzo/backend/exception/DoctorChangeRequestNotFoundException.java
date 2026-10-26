package com.wrapzzo.backend.exception;

import java.util.UUID;

public class DoctorChangeRequestNotFoundException extends RuntimeException {

    public DoctorChangeRequestNotFoundException(UUID id) {
        super("Doctor change request not found: " + id);
    }
}
