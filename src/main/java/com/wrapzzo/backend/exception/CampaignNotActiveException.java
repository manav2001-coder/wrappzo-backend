package com.wrapzzo.backend.exception;

import java.util.UUID;

/** A Draft campaign has no live submission window yet -- it must be activated first. */
public class CampaignNotActiveException extends RuntimeException {

    public CampaignNotActiveException(UUID campaignId) {
        super("Campaign is not active: " + campaignId);
    }
}
