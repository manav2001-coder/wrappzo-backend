package com.wrapzzo.backend.exception;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(UUID id) {
        super("Role not found: " + id);
    }
}
