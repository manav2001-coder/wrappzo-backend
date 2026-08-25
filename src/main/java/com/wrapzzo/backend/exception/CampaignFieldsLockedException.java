package com.wrapzzo.backend.exception;

import java.util.UUID;

/** The field list can only be edited while a campaign is still Draft -- once Active, submissions may reference these fields. */
public class CampaignFieldsLockedException extends RuntimeException {

    public CampaignFieldsLockedException(UUID campaignId) {
        super("Campaign fields can only be edited while the campaign is a draft: " + campaignId);
    }
}
