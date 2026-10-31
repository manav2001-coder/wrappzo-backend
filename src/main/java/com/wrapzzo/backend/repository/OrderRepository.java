package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** A company's orders the caller can see (see Visibility): by owner, or by the org unit they're stamped with. */
    @Query("""
            select o from Order o
            where o.company.id = :companyId
              and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)
            order by o.createdAt desc
            """)
    List<Order> findVisibleInCompany(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    List<Order> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    /** A company's warehouse-routed orders in the given stages -- the warehouse's work queue. */
    List<Order> findByCompanyIdAndWarehouseIsNotNullAndStatusInOrderByUpdatedAtDesc(UUID companyId, Collection<OrderStatus> statuses);

    /** A company's Quick Gift orders -- no campaign to key off, so this is the bulk-assign source for that pseudo-group. */
    List<Order> findByCompanyIdAndCampaignIdIsNullOrderByCreatedAtDesc(UUID companyId);

    /** A vendor's own fulfillment queue -- every order routed to them, across companies. */
    List<Order> findByAssignedVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Optional<Order> findBySubmissionId(UUID submissionId);

    /** Cross-company -- Admin's Orders oversight (PRD Part 1.5), not scoped to one company. */
    List<Order> findAllByOrderByCreatedAtDesc();

    /** Naive load-balancing input for auto-assign: how many in-flight orders a vendor already has. */
    long countByAssignedVendorIdAndStatusIn(UUID vendorId, List<OrderStatus> statuses);

    /** One batched query for every campaign's order count -- not a per-campaign query (PRD 4.7). */
    @Query("select o.campaign.id as campaignId, count(o) as cnt from Order o where o.campaign.id in :campaignIds group by o.campaign.id")
    List<CampaignOrderCount> countByCampaignIdIn(@Param("campaignIds") Collection<UUID> campaignIds);
}
