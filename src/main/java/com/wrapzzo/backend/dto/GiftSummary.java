package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Gift;
import java.util.UUID;

public record GiftSummary(UUID id, String name) {
    public static GiftSummary from(Gift gift) {
        return new GiftSummary(gift.getId(), gift.getName());
    }
}
