package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDesignRepository extends JpaRepository<OrderDesign, UUID> {

    List<OrderDesign> findByOrder_IdOrderByVersionNoDesc(UUID orderId);

    Optional<OrderDesign> findTopByOrder_IdOrderByVersionNoDesc(UUID orderId);

    int countByOrder_Id(UUID orderId);

    boolean existsByOrder_IdAndStatus(UUID orderId, OrderDesignStatus status);

    List<OrderDesign> findByStatusAndOrder_Company_IdOrderByUpdatedAtAsc(OrderDesignStatus status, UUID companyId);

    /** Proofs at a given status whose order is within a caller's reach -- the candidates for their queue, narrowed afterwards to the ones that are theirs to sign. */
    @org.springframework.data.jpa.repository.Query("""
            select d from OrderDesign d
            where d.status = :status and d.order.company.id = :companyId
              and (:companyWide = true or d.order.owner.id in :people or d.order.orgUnit.id in :orgUnits)
            order by d.updatedAt asc
            """)
    List<OrderDesign> findByStatusVisible(@org.springframework.data.repository.query.Param("status") OrderDesignStatus status,
            @org.springframework.data.repository.query.Param("companyId") UUID companyId, @org.springframework.data.repository.query.Param("companyWide") boolean companyWide,
            @org.springframework.data.repository.query.Param("people") Collection<UUID> people, @org.springframework.data.repository.query.Param("orgUnits") Collection<UUID> orgUnits);

    /** Every proof of several orders, newest version first within each -- one query for the vendor's order queue. */
    List<OrderDesign> findByOrder_IdInOrderByVersionNoDesc(Collection<UUID> orderIds);

    /** Batch "which of these orders has a design awaiting review" -- for the Orders list's at-a-glance indicator, not N+1 per order. */
    List<OrderDesign> findByOrder_IdInAndStatus(Collection<UUID> orderIds, OrderDesignStatus status);

    /** Proofs waiting on someone that have had fewer than maxReminders reminders, oldest wait first. */
    List<OrderDesign> findByStatusInAndRemindersSentLessThanOrderByAwaitingSinceAsc(Collection<OrderDesignStatus> statuses, int maxReminders);
}
