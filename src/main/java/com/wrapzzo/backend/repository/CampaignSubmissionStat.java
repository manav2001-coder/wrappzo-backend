package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.SubmissionStatus;
import java.util.UUID;

/** One (campaign, status) count -- batched dashboard aggregation, not a per-campaign query (PRD 4.7). */
public interface CampaignSubmissionStat {
    UUID getCampaignId();

    SubmissionStatus getStatus();

    long getCnt();
}
