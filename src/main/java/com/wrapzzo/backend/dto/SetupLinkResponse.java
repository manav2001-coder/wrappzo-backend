package com.wrapzzo.backend.dto;

import java.time.Instant;

/** A person's password-setup link, for the admin to pass on, and when it stops working. It works once. */
public record SetupLinkResponse(String setupUrl, Instant expiresAt) {
}
