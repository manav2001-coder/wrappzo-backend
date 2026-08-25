package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignAudienceType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CampaignAudienceRequest(@NotNull CampaignAudienceType type, UUID orgUnitId, UUID anchorUserId, UUID userId) {
}
