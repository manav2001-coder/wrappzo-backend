package com.wrapzzo.backend.model;

/** Mirrors SubmissionStatus's approval workflow -- Quick Gift goes through the same Company Admin gate. */
public enum QuickGiftStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
