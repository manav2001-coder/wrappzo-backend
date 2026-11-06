package com.wrapzzo.backend.service;

import java.util.UUID;

/** An order was placed and now waits on its approver; published inside the placing transaction, acted on after commit. */
public record OrderAwaitingApprovalEvent(UUID orderId) {
}
