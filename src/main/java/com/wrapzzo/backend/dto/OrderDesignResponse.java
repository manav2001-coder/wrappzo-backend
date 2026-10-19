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
                design.getReviewComment(),
                design.getReviewedAt(),
                design.getCreatedAt());
    }
}
