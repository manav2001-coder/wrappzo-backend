package com.wrapzzo.backend.service;

/**
 * Plain data, deliberately -- not a User reference. This crosses to a different thread
 * after the transaction that created it has already committed (see UserInviteEmailListener),
 * so it can't lazily load anything off an entity; everything the email needs must already
 * be captured here.
 */
public record PendingInvite(String email, String name, String setupUrl, long expiryHours) {
}
