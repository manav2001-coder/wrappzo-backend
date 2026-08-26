package com.wrapzzo.backend.service;

/**
 * Plain data, deliberately -- not entity references. This crosses to a different thread
 * after the transaction that created it has already committed (see
 * RecipientPhotoRequestEmailListener), so it can't lazily load anything off an entity;
 * everything the email needs must already be captured here.
 */
public record PendingRecipientPhotoRequest(
        String recipientEmail, String recipientName, String companyName, String giftName, String uploadUrl, long expiryHours) {
}
