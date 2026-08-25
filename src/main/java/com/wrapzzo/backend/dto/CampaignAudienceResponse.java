package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignAudience;
import com.wrapzzo.backend.model.CampaignAudienceType;
import java.util.UUID;

public record CampaignAudienceResponse(
        UUID id,
        CampaignAudienceType type,
        UUID orgUnitId,
        String orgUnitName,
        UUID anchorUserId,
        String anchorUserName,
        UUID userId,
        String userName) {

    public static CampaignAudienceResponse from(CampaignAudience audience) {
        return new CampaignAudienceResponse(
                audience.getId(),
                audience.getType(),
                audience.getOrgUnit() != null ? audience.getOrgUnit().getId() : null,
                audience.getOrgUnit() != null ? audience.getOrgUnit().getName() : null,
                audience.getAnchorUser() != null ? audience.getAnchorUser().getId() : null,
                audience.getAnchorUser() != null ? audience.getAnchorUser().getName() : null,
                audience.getUser() != null ? audience.getUser().getId() : null,
                audience.getUser() != null ? audience.getUser().getName() : null);
    }
}
