package com.wrapzzo.backend.model;

/** Where to find the person holding an approval step's role, for the order at hand. */
public enum ArtworkApprovalScope {
    /** The nearest manager above the AM (users.reports_to_id, upward) who holds the role. */
    REPORTING_LINE,
    /** Anyone holding the role in the AM's division -- the root org unit above them; the first to act decides. */
    DIVISION
}
