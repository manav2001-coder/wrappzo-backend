package com.wrapzzo.backend.exception;

import java.util.UUID;

public class UserHasActivityException extends RuntimeException {

    public UserHasActivityException(UUID id) {
        super("User " + id + " can't be deleted -- they have campaigns, submissions, or orders on record that reference them.");
    }
}
