package com.wrapzzo.backend.model;

/** Mirrors QuickGiftStatus's approve/reject shape -- rejection here means "send back for rework", not a dead end. */
public enum OrderDesignStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
