package com.wrapzzo.backend.exception;

import java.util.UUID;

public class NotInCampaignAudienceException extends RuntimeException {

    public NotInCampaignAudienceException(UUID campaignId) {
        super("You are not in the audience for this campaign: " + campaignId);
    }
}
