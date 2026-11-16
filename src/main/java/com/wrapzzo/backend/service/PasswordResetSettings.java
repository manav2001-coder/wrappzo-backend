package com.wrapzzo.backend.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** How long a reset link works and how often one may be asked for -- in one place, for people and vendors alike. */
@Component
public class PasswordResetSettings {

    private final long expiryMinutes;
    private final long cooldownMinutes;
    private final int maxPerDay;
    private final int globalDailyCap;

    public PasswordResetSettings(
            @Value("${auth.password-reset.expiry-minutes}") long expiryMinutes,
            @Value("${auth.password-reset.cooldown-minutes}") long cooldownMinutes,
            @Value("${auth.password-reset.max-per-day}") int maxPerDay,
            @Value("${auth.password-reset.global-daily-cap}") int globalDailyCap) {
        this.expiryMinutes = expiryMinutes;
        this.cooldownMinutes = cooldownMinutes;
        this.maxPerDay = maxPerDay;
        this.globalDailyCap = globalDailyCap;
    }

    public long expiryMinutes() {
        return expiryMinutes;
    }

    public Duration expiry() {
        return Duration.ofMinutes(expiryMinutes);
    }

    public Duration cooldown() {
        return Duration.ofMinutes(cooldownMinutes);
    }

    public int maxPerDay() {
        return maxPerDay;
    }

    public int globalDailyCap() {
        return globalDailyCap;
    }
}
