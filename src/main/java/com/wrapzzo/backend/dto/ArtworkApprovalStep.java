package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderDesignApproval;
import com.wrapzzo.backend.model.OrderDesignDecision;
import java.time.Instant;

/** One row of a proof's approval trail. Level 0 is the MR's review; {@code onBehalfOfName} is set when someone covered a level for its manager. */
public record ArtworkApprovalStep(
        int level,
        OrderDesignDecision decision,
        String decidedByName,
        String onBehalfOfName,
        String comment,
        Instant decidedAt) {

    public static ArtworkApprovalStep from(OrderDesignApproval approval) {
        return new ArtworkApprovalStep(
                approval.getLevel(),
                approval.getDecision(),
                approval.getDecidedBy().getName(),
                approval.getOnBehalfOf() != null ? approval.getOnBehalfOf().getName() : null,
                approval.getComment(),
                approval.getDecidedAt());
    }
}
