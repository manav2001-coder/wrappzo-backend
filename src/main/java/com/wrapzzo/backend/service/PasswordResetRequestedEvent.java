package com.wrapzzo.backend.service;

/** A reset link has been saved; the email carrying it goes out after commit, off the request thread. Plain data: it crosses threads. */
public record PasswordResetRequestedEvent(String email, String name, String resetUrl, long expiryMinutes) {
}
