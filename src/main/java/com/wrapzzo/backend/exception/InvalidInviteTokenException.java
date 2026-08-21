package com.wrapzzo.backend.exception;

public class InvalidInviteTokenException extends RuntimeException {

    public InvalidInviteTokenException() {
        super("This invite link is invalid, expired, or already used");
    }
}
