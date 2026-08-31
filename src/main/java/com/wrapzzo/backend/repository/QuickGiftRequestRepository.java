package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuickGiftRequestRepository extends JpaRepository<QuickGiftRequest, UUID> {

    List<QuickGiftRequest> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    List<QuickGiftRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<QuickGiftRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, QuickGiftStatus status);
}
