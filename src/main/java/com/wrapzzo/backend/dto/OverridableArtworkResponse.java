package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderDesign;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A proof still waiting on the MR or a level below the caller, which the caller may approve in place of the people there --
 * one row of their "Waiting below you" list. {@code skipping} names each step their approval would skip.
 */
public record OverridableArtworkResponse(
        UUID designId,
        UUID orderId,
        String orderDisplayId,
        String giftName,
        String recipientName,
        int quantity,
        String requestedByName,
        int versionNo,
        String fileUrl,
        String fileName,
        /** What the proof waits on now: "MR review", or the level and who it names. */
        String waitingOn,
        /** The level it waits on: 0 for the MR's review, then 1..n. */
        int waitingLevel,
        /** The level the caller would approve at, which the proof moves on from. */
        int yourLevel,
        int approvalLevels,
        /** Each step the caller's approval would skip, nearest first: "MR review (Asha Rao)", "Level 1 (Priya Shah)". */
        List<String> skipping,
        Instant waitingSince,
        List<ArtworkApprovalStep> approvals) {

    public static OverridableArtworkResponse from(OrderDesign design, String fileUrl, String requesterName, String waitingOn, int waitingLevel,
            int yourLevel, int approvalLevels, List<String> skipping, List<ArtworkApprovalStep> approvals) {
        var order = design.getOrder();
        return new OverridableArtworkResponse(
                design.getId(),
                order.getId(),
                "ORD-" + order.getOrderNo(),
                order.getGift() != null ? order.getGift().getName() : null,
                order.getRecipientName(),
                order.getQuantity(),
                requesterName,
                design.getVersionNo(),
                fileUrl,
                design.getFileName(),
                waitingOn,
                waitingLevel,
                yourLevel,
                approvalLevels,
                skipping,
                design.getAwaitingSince(),
                approvals);
    }
}
