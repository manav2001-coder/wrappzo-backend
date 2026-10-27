package com.wrapzzo.backend.model;

/** Mirrors QuickGiftStatus's approve/reject shape -- rejection here means "send back for rework", not a dead end. */
public enum OrderDesignStatus {
    PENDING_REVIEW,
    /** The MR approved it; with senior artwork approval on, it waits for their manager before print. */
    PENDING_FINAL_APPROVAL,
    APPROVED,
    REJECTED
}
