package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrderStatusHistory;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrderStatusHistoryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every stage an order passes through. Called explicitly alongside every
 * Order.setStatus() (creation's initial PROCESSING included) rather than an entity
 * listener, so each call site stays an obvious, greppable "this changed status, so also
 * record it" pair instead of implicit magic.
 */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    public void record(Order order, OrderStatus status, User changedBy) {
        orderStatusHistoryRepository.save(new OrderStatusHistory(order, status, changedBy));
    }

    /** Every history row for a batch of orders, grouped by order id, oldest-first within each order. */
    @Transactional(readOnly = true)
    public Map<UUID, List<OrderStatusHistory>> historyByOrderId(Collection<UUID> orderIds) {
        return orderStatusHistoryRepository.findByOrder_IdInOrderByChangedAtAsc(orderIds).stream()
                .collect(Collectors.groupingBy(h -> h.getOrder().getId()));
    }
}
