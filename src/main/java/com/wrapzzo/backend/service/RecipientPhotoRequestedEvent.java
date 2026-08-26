package com.wrapzzo.backend.service;

/** Published once the upload token is saved; the actual email goes out after commit, off the request thread. */
public record RecipientPhotoRequestedEvent(PendingRecipientPhotoRequest request) {
}
