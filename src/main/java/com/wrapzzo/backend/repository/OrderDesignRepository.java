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

    /** Every proof of several orders, newest version first within each -- one query for the vendor's order queue. */
    List<OrderDesign> findByOrder_IdInOrderByVersionNoDesc(Collection<UUID> orderIds);

    /** Batch "which of these orders has a design awaiting review" -- for the Orders list's at-a-glance indicator, not N+1 per order. */
    List<OrderDesign> findByOrder_IdInAndStatus(Collection<UUID> orderIds, OrderDesignStatus status);
}
