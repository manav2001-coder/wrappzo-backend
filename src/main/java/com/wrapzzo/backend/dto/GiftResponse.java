package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record GiftResponse(
        UUID id,
        String displayId,
        String name,
        String category,
        String description,
        Integer moq,
        Integer leadTimeDays,
        boolean active,
        boolean photoRequired,
        boolean artworkRequired,
        Set<GiftModule> modules,
        BigDecimal price,
        BigDecimal markupFeePercentage,
        BigDecimal commissionFeePercentage,
        BigDecimal totalPrice,
        UUID vendorId,
        String vendorName,
        List<GiftImageResponse> images,
        Instant createdAt) {

    public static GiftResponse from(Gift gift, List<GiftImageResponse> images) {
        return new GiftResponse(
                gift.getId(),
                "GID-" + gift.getGiftNo(),
                gift.getName(),
                gift.getCategory(),
                gift.getDescription(),
                gift.getMoq(),
                gift.getLeadTimeDays(),
                gift.isActive(),
                gift.isPhotoRequired(),
                gift.isArtworkRequired(),
                gift.getModules(),
                gift.getPrice(),
                gift.getMarkupFeePercentage(),
                gift.getCommissionFeePercentage(),
                gift.getTotalPrice(),
                gift.getVendor() != null ? gift.getVendor().getId() : null,
                gift.getVendor() != null ? gift.getVendor().getName() : null,
                images,
                gift.getCreatedAt());
    }
}
