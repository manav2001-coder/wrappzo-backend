package com.wrapzzo.backend.service;

import java.util.UUID;

/** An artwork proof just started waiting on an approver (uploaded, or moved up a level); they are emailed after commit. */
public record ArtworkAwaitingApprovalEvent(UUID designId) {
}
