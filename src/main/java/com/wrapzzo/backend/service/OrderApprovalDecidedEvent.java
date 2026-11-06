package com.wrapzzo.backend.service;

import java.util.UUID;

/** The approver approved or turned down an order; the requester is told after commit. */
public record OrderApprovalDecidedEvent(UUID orderId) {
}
