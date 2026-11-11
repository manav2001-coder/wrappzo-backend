package com.wrapzzo.backend.model;

/** One reviewer's verdict on a proof -- a row of its approval trail (order_design_approvals). */
public enum OrderDesignDecision {
    APPROVED,
    REJECTED,
    /** This level was skipped: someone higher in the cycle approved the proof in place of the people it was waiting on. */
    BYPASSED
}
