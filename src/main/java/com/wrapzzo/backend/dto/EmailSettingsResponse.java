package com.wrapzzo.backend.dto;

import java.time.Instant;

/** Platform config > Email settings: whether the order-workflow emails are on, and who last changed it and when. */
public record EmailSettingsResponse(boolean workflowEmailsEnabled, Instant updatedAt, String updatedByName) {
}
