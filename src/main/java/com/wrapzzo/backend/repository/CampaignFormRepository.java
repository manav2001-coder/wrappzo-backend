package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CampaignForm;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignFormRepository extends JpaRepository<CampaignForm, UUID> {

    Optional<CampaignForm> findByCampaignId(UUID campaignId);
}
