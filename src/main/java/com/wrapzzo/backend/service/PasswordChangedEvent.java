package com.wrapzzo.backend.service;

import java.time.Instant;

/** A password was changed by a reset; the person is told by email, so a change they did not make is noticed. */
public record PasswordChangedEvent(String email, String name, Instant changedAt) {
}
