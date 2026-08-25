package com.wrapzzo.backend.repository;

import java.util.UUID;

public interface CampaignOrderCount {
    UUID getCampaignId();

    long getCnt();
}
