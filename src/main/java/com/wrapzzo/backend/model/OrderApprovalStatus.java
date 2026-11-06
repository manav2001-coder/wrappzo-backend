package com.wrapzzo.backend.model;

/** Where an order stands on its company's order approval (V74): the approver's yes or no comes before any vendor sees it. */
public enum OrderApprovalStatus {
    /** Approval was off (or the requester is the approver), so the order went straight to the vendor. */
    NOT_REQUIRED,
    /** Waiting for the approver; the order is not with a vendor yet. */
    PENDING,
    APPROVED,
    /** Turned down; the order is cancelled and never reaches a vendor. */
    REJECTED
}
