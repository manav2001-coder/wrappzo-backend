package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignPurpose;
import com.wrapzzo.backend.model.CampaignRecipientCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull CampaignPurpose purpose,
        @NotNull CampaignRecipientCategory recipientCategory,
        @NotEmpty Set<UUID> giftIds,
        String instructions,
        Instant submissionDeadline) {
}
