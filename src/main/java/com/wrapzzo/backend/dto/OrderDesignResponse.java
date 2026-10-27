package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import java.time.Instant;
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
        Instant createdAt) {

    public static OrderDesignResponse from(OrderDesign design, String fileUrl) {
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
                design.getCreatedAt());
    }
}
