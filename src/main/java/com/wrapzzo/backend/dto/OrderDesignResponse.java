package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDesignResponse(
        UUID id,
        UUID orderId,
        int versionNo,
        String fileUrl,
        String fileName,
        OrderDesignStatus status,
        String uploadedByVendorName,
        String reviewedByUserName,
        /** The MR's approval, when the proof then went to a senior for the final one. */
        String firstApprovedByUserName,
        Instant firstApprovedAt,
        String reviewComment,
        Instant reviewedAt,
        /** While PENDING_FINAL_APPROVAL: the level it waits on, of how many (1 = the MR's manager). */
        Integer approvalLevel,
        Integer approvalLevels,
        /** The manager the current level waits on; null when no one is above the MR (a Final Artwork Approval holder signs). Company side only. */
        String waitingOnName,
        /** Every decision on this proof, oldest first. Company side only -- empty for the vendor. */
        List<ArtworkApprovalStep> approvals,
        Instant createdAt) {

    public static OrderDesignResponse from(OrderDesign design, String fileUrl, List<ArtworkApprovalStep> approvals, String waitingOnName) {
        return new OrderDesignResponse(
                design.getId(),
                design.getOrder().getId(),
                design.getVersionNo(),
                fileUrl,
                design.getFileName(),
                design.getStatus(),
                design.getUploadedBy().getName(),
                design.getReviewedBy() != null ? design.getReviewedBy().getName() : null,
                design.getFirstApprovedBy() != null ? design.getFirstApprovedBy().getName() : null,
                design.getFirstApprovedAt(),
                design.getReviewComment(),
                design.getReviewedAt(),
                design.getApprovalLevel(),
                design.getApprovalLevels(),
                waitingOnName,
                approvals,
                design.getCreatedAt());
    }
}
