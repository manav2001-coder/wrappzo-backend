package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformDashboardServiceTest {

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GiftRepository gifts = mock(GiftRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PlatformDashboardService service = new PlatformDashboardService(companies, vendors, users, gifts, orders);

    private static OrderStatusCount row(OrderStatus status, long count) {
        OrderStatusCount row = mock(OrderStatusCount.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getCnt()).thenReturn(count);
        return row;
    }

    @Test
    void countsEveryTableAndEveryOrderStatus() {
        when(companies.count()).thenReturn(5L);
        when(companies.countByStatus(CompanyStatus.ACTIVE)).thenReturn(4L);
        when(vendors.count()).thenReturn(3L);
        when(vendors.countByStatus(VendorStatus.ACTIVE)).thenReturn(2L);
        when(users.count()).thenReturn(40L);
        when(gifts.count()).thenReturn(12L);
        when(gifts.countByActiveTrue()).thenReturn(10L);
        List<OrderStatusCount> rows = List.of(row(OrderStatus.PROCESSING, 7), row(OrderStatus.DELIVERED, 3));
        when(orders.countByStatus()).thenReturn(rows);

        PlatformDashboardResponse summary = service.summary();

        assertThat(summary.companies()).isEqualTo(5);
        assertThat(summary.activeCompanies()).isEqualTo(4);
        assertThat(summary.vendors()).isEqualTo(3);
        assertThat(summary.activeVendors()).isEqualTo(2);
        assertThat(summary.users()).isEqualTo(40);
        assertThat(summary.gifts()).isEqualTo(12);
        assertThat(summary.activeGifts()).isEqualTo(10);
        assertThat(summary.orders()).isEqualTo(10);
        assertThat(summary.ordersByStatus()).hasSize(OrderStatus.values().length);
        assertThat(summary.ordersByStatus()).containsEntry("PROCESSING", 7L).containsEntry("DELIVERED", 3L).containsEntry("DISPATCHED", 0L);
    }
}
