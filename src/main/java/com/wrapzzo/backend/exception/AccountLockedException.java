package com.wrapzzo.backend.exception;

import java.time.Instant;
import lombok.Getter;

@Getter
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("Account is temporarily locked due to too many failed login attempts");
        this.lockedUntil = lockedUntil;
    }
}
