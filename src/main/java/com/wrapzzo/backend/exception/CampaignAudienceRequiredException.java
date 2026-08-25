package com.wrapzzo.backend.exception;

import java.util.UUID;

/** A campaign with no audience rows can never be reached by a field user -- refuse to activate one. */
public class CampaignAudienceRequiredException extends RuntimeException {

    public CampaignAudienceRequiredException(UUID campaignId) {
        super("Add at least one audience rule before activating this campaign: " + campaignId);
    }
}
