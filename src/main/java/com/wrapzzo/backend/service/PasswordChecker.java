package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.ServerBusyException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks a typed password against its hash, a few at a time. The hash is deliberately slow and memory-hungry (that is what makes a stolen
 * database hard to crack), so a rush of sign-ins must queue HERE -- holding nothing -- rather than all run at once: they would each slow
 * the others and everyone else's requests, and if each held a database connection while it waited, the pool would run dry.
 * A caller must not be inside a transaction when it calls this.
 */
@Component
public class PasswordChecker {

    private final PasswordEncoder encoder;
    private final Semaphore slots;
    private final long waitSeconds;

    public PasswordChecker(
            PasswordEncoder encoder,
            @Value("${auth.max-concurrent-password-checks:0}") int configured,
            @Value("${auth.password-check-max-wait-seconds:30}") long waitSeconds) {
        this.encoder = encoder;
        // Default: one fewer than the processors (at least one), so a rush of sign-ins can never take every core from everyone else's requests.
        this.slots = new Semaphore(configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors() - 1), true);
        this.waitSeconds = waitSeconds;
    }

    public boolean matches(String rawPassword, String hash) {
        try {
            if (!slots.tryAcquire(waitSeconds, TimeUnit.SECONDS)) {
                throw busy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        try {
            return encoder.matches(rawPassword, hash);
        } finally {
            slots.release();
        }
    }

    private static ServerBusyException busy() {
        return new ServerBusyException("Signing in is very busy right now -- please try again in a few seconds.");
    }
}
