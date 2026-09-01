package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuickGiftRequestRepository extends JpaRepository<QuickGiftRequest, UUID> {

    List<QuickGiftRequest> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    List<QuickGiftRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<QuickGiftRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, QuickGiftStatus status);

    /** The reverse of QuickGiftRequest.order (a @OneToOne with the FK on this side) -- lets Order-facing code find its originating request. */
    Optional<QuickGiftRequest> findByOrderId(UUID orderId);
}
