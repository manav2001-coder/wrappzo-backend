package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Gift;
import java.time.Instant;
import java.util.List;
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
                images,
                gift.getCreatedAt());
    }
}
