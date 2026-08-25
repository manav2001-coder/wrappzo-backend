package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CampaignField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignFieldRepository extends JpaRepository<CampaignField, UUID> {

    List<CampaignField> findByCampaignFormIdOrderByDisplayOrderAsc(UUID campaignFormId);
}
