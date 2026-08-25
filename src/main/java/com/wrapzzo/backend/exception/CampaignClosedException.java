package com.wrapzzo.backend.exception;

import java.util.UUID;

/** PRD 2.9/4.6 -- a Closed campaign accepts no new submissions and creates no new Orders. */
public class CampaignClosedException extends RuntimeException {

    public CampaignClosedException(UUID campaignId) {
        super("Campaign is closed: " + campaignId);
    }
}
