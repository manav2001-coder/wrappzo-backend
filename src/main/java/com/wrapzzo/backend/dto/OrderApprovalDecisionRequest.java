package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Size;

/** The approver's note: optional on approving, required on turning an order down (checked by the service, with its own message). */
public record OrderApprovalDecisionRequest(@Size(max = 1000, message = "The note can be at most 1000 characters.") String comment) {
}
