package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderStatusHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

    List<OrderStatusHistory> findByOrder_IdInOrderByChangedAtAsc(Collection<UUID> orderIds);
}
