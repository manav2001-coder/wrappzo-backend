package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CampaignAudience;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignAudienceRepository extends JpaRepository<CampaignAudience, UUID> {

    List<CampaignAudience> findByCampaignId(UUID campaignId);

    void deleteByCampaignId(UUID campaignId);
}
