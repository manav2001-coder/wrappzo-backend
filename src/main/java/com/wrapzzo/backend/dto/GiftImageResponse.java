package com.wrapzzo.backend.dto;

import java.util.UUID;

public record GiftImageResponse(UUID id, String imageUrl, int displayOrder) {
}
