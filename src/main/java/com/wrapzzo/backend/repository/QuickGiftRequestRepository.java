package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface QuickGiftRequestRepository extends JpaRepository<QuickGiftRequest, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<QuickGiftRequest> {



    /** The reverse of QuickGiftRequest.order (a @OneToOne with the FK on this side) -- lets Order-facing code find its originating request. */
    Optional<QuickGiftRequest> findByOrderId(UUID orderId);

    /** findByOrderId for a whole list of orders in one query -- the vendor's order queue. */
    List<QuickGiftRequest> findByOrder_IdIn(Collection<UUID> orderIds);
}
