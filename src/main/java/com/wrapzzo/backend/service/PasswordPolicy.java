package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.WeakPasswordException;

/** What a password must be, in one place, so every screen that sets one asks the same of it. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** Throws {@link WeakPasswordException}, saying what is wrong, when the password is not acceptable. */
    public static void require(String password, String currentPassword) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new WeakPasswordException("The new password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            throw new WeakPasswordException("The new password must be at most " + MAX_LENGTH + " characters long");
        }
        if (password.equals(currentPassword)) {
            throw new WeakPasswordException("The new password must be different from your current one");
        }
    }
}
