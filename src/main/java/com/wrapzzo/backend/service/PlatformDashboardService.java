package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PlatformDashboardResponse;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrderStatusCount;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wrappzo staff's overview: counts across every company, each one a single aggregate query. */
@Service
@RequiredArgsConstructor
public class PlatformDashboardService {

    private final CompanyRepository companyRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final GiftRepository giftRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PlatformDashboardResponse summary() {
        EnumMap<OrderStatus, Long> counted = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) counted.put(status, 0L);
        for (OrderStatusCount row : orderRepository.countByStatus()) counted.put(row.getStatus(), row.getCnt());

        // Every status appears, in pipeline order, so the chart does not lose a bar when a stage is empty.
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        counted.forEach((status, count) -> ordersByStatus.put(status.name(), count));

        return new PlatformDashboardResponse(
                companyRepository.count(),
                companyRepository.countByStatus(CompanyStatus.ACTIVE),
                vendorRepository.count(),
                vendorRepository.countByStatus(VendorStatus.ACTIVE),
                userRepository.count(),
                giftRepository.count(),
                giftRepository.countByActiveTrue(),
                counted.values().stream().mapToLong(Long::longValue).sum(),
                ordersByStatus);
    }
}
