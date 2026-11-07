package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {

    /** Counts per status of the orders a caller can see -- the stat cards over a list, without loading it. */
    @Query("""
            select o.status, count(o) from Order o
            where o.company.id = :companyId
              and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)
            group by o.status
            """)
    List<Object[]> countVisibleByStatus(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    @Query("""
            select count(o) from Order o
            where o.company.id = :companyId
              and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)
              and exists (select 1 from OrderDesign d where d.order = o and d.status = com.wrapzzo.backend.model.OrderDesignStatus.PENDING_REVIEW)
            """)
    long countVisibleAwaitingReview(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    @Query("select o.status, count(o) from Order o where o.company.id = :companyId group by o.status")
    List<Object[]> countByStatusInCompany(@Param("companyId") UUID companyId);

    /** Live orders with no vendor yet that are not held for their approver. */
    @Query("""
            select count(o) from Order o
            where o.company.id = :companyId and o.assignedVendor is null
              and o.status <> com.wrapzzo.backend.model.OrderStatus.CANCELLED
              and o.approvalStatus <> com.wrapzzo.backend.model.OrderApprovalStatus.PENDING
            """)
    long countUnassignedInCompany(@Param("companyId") UUID companyId);

    /** Orders waiting on an approver, within a caller's reach -- the candidates for their queue, which is then narrowed to the ones that are theirs to decide. */
    @Query("""
            select o from Order o
            where o.company.id = :companyId and o.approvalStatus = com.wrapzzo.backend.model.OrderApprovalStatus.PENDING
              and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)
            order by o.createdAt asc
            """)
    List<Order> findPendingApprovalVisible(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    /** How many orders the vendor has from each division (V78). */
    @Query("select o.divisionId, count(o) from Order o where o.assignedVendor.id = :vendorId and o.divisionId is not null group by o.divisionId")
    List<Object[]> countByDivisionForVendor(@Param("vendorId") UUID vendorId);

    List<Order> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    /** Every order still waiting on its approver that has had fewer than {@code max} reminders. */
    List<Order> findByApprovalStatusAndApprovalRemindersSentLessThanOrderByCreatedAtAsc(OrderApprovalStatus approvalStatus, int max);

    /** A company's orders waiting on their approver (V74), oldest first -- the approvers' queue. */
    List<Order> findByCompanyIdAndApprovalStatusOrderByCreatedAtAsc(UUID companyId, OrderApprovalStatus approvalStatus);

    /** A company's Quick Gift orders -- no campaign to key off, so this is the bulk-assign source for that pseudo-group. */
    List<Order> findByCompanyIdAndCampaignIdIsNullOrderByCreatedAtDesc(UUID companyId);

    /** A vendor's own fulfillment queue -- every order routed to them, across companies. */
    List<Order> findByAssignedVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Optional<Order> findBySubmissionId(UUID submissionId);

    /** Cross-company -- Admin's Orders oversight (PRD Part 1.5), not scoped to one company. */

    /** Naive load-balancing input for auto-assign: how many in-flight orders a vendor already has. */
    long countByAssignedVendorIdAndStatusIn(UUID vendorId, List<OrderStatus> statuses);

    /** One batched query for every campaign's order count -- not a per-campaign query (PRD 4.7). */
    @Query("select o.campaign.id as campaignId, count(o) as cnt from Order o where o.campaign.id in :campaignIds group by o.campaign.id")
    List<CampaignOrderCount> countByCampaignIdIn(@Param("campaignIds") Collection<UUID> campaignIds);
}
