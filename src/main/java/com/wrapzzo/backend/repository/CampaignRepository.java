package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Campaign;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
