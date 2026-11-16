package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Takes a "forgot password" request off the web thread. The caller is answered at once, with the same words whether or not the
 * account exists, and the lookup, the limits and the email happen here -- so how long the answer takes gives nothing away either.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetDispatcher {

    private final PasswordResetService users;
    private final VendorPasswordResetService vendors;

    /** The email belongs to a person, or else to a vendor: the one sign-in screen serves both, so one page asks for both. */
    @Async("emailTaskExecutor")
    public void forEmail(String email) {
        try {
            if (!users.requestReset(email)) {
                vendors.requestReset(email);
            }
        } catch (RuntimeException e) {
            log.warn("Password reset request could not be processed", e);
        }
    }
}
