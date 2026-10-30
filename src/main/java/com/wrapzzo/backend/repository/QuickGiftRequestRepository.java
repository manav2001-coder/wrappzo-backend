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

public interface QuickGiftRequestRepository extends JpaRepository<QuickGiftRequest, UUID> {

    List<QuickGiftRequest> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    List<QuickGiftRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** The reverse of QuickGiftRequest.order (a @OneToOne with the FK on this side) -- lets Order-facing code find its originating request. */
    Optional<QuickGiftRequest> findByOrderId(UUID orderId);

    /** findByOrderId for a whole list of orders in one query -- the vendor's order queue. */
    List<QuickGiftRequest> findByOrder_IdIn(Collection<UUID> orderIds);

    /** A company's requests in one status that the caller can see (see Visibility): by requester or org unit. */
    @Query("""
            select r from QuickGiftRequest r
            where r.company.id = :companyId and r.status = :status
              and (:companyWide = true or r.requestedBy.id in :people or r.orgUnit.id in :orgUnits)
            order by r.createdAt desc
            """)
    List<QuickGiftRequest> findVisibleInCompanyByStatus(@Param("companyId") UUID companyId, @Param("status") QuickGiftStatus status,
            @Param("companyWide") boolean companyWide, @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);
}
