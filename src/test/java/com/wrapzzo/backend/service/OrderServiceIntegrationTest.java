package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.exception.InvalidOrderStatusTransitionException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** CurrentVendor reads the vendor id straight off the Authentication principal -- set up manually, same as CurrentUser elsewhere. */
@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private GiftRepository giftRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User creator;
    private Vendor vendor;
    private Vendor otherVendor;
    private Gift gift;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("Order Test Co"));
        Role role = roleRepository.save(new Role(company, "Whatever Role"));
        creator = userRepository.save(new User(company, role, "Test Admin", "order-test-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        vendor = vendorRepository.save(new Vendor("Acme Printing", "acme-" + UUID.randomUUID() + "@test.com", null, "n/a", creator));
        otherVendor = vendorRepository.save(new Vendor("Other Printing", "other-" + UUID.randomUUID() + "@test.com", null, "n/a", creator));
        gift = giftRepository.save(new Gift("Mug", "Drinkware", "A mug", null, null, creator));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAsVendor(Vendor v) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(v.getId(), null, List.of()));
    }

    private Order newOrder(Vendor assignedVendor, OrderStatus status) {
        Order order = new Order(creator.getCompany(), gift, OrderSource.QUICK_GIFT, "Jane Doe", "9999999999", creator);
        order.setAssignedVendor(assignedVendor);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Test
    void listForVendorReturnsOnlyOrdersAssignedToThatVendor() {
        Order mine = newOrder(vendor, OrderStatus.PRODUCTION_QUEUE);
        newOrder(otherVendor, OrderStatus.PRODUCTION_QUEUE);
        newOrder(null, OrderStatus.PROCESSING);

        actAsVendor(vendor);
        List<VendorOrderResponse> orders = orderService.listForVendor(vendor.getId());

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).id()).isEqualTo(mine.getId());
    }

    @Test
    void getForVendorThrowsWhenOrderBelongsToAnotherVendor() {
        Order theirs = newOrder(otherVendor, OrderStatus.PRODUCTION_QUEUE);

        actAsVendor(vendor);
        assertThatThrownBy(() -> orderService.getForVendor(theirs.getId(), vendor.getId()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatusForVendorMarksDeliveredFromProductionQueue() {
        Order order = newOrder(vendor, OrderStatus.PRODUCTION_QUEUE);

        actAsVendor(vendor);
        VendorOrderResponse delivered = orderService.updateStatusForVendor(order.getId(), vendor.getId(), OrderStatus.DELIVERED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void updateStatusForVendorMarksDeliveredFromDispatched() {
        Order order = newOrder(vendor, OrderStatus.DISPATCHED);

        actAsVendor(vendor);
        VendorOrderResponse delivered = orderService.updateStatusForVendor(order.getId(), vendor.getId(), OrderStatus.DELIVERED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void updateStatusForVendorRejectsSettingDispatchedDirectly() {
        Order order = newOrder(vendor, OrderStatus.PRODUCTION_QUEUE);

        actAsVendor(vendor);
        assertThatThrownBy(() -> orderService.updateStatusForVendor(order.getId(), vendor.getId(), OrderStatus.DISPATCHED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateStatusForVendorRejectsAnotherVendorsOrder() {
        Order theirs = newOrder(otherVendor, OrderStatus.PRODUCTION_QUEUE);

        actAsVendor(vendor);
        assertThatThrownBy(() -> orderService.updateStatusForVendor(theirs.getId(), vendor.getId(), OrderStatus.DELIVERED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listForVendorPriceExcludesMarkupButIncludesCommission() {
        gift.setPrice(BigDecimal.valueOf(1000));
        gift.setMarkupFeePercentage(BigDecimal.valueOf(20));
        gift.setCommissionFeePercentage(BigDecimal.valueOf(10));
        giftRepository.save(gift);
        newOrder(vendor, OrderStatus.PRODUCTION_QUEUE);

        actAsVendor(vendor);
        List<VendorOrderResponse> orders = orderService.listForVendor(vendor.getId());

        // 1000 + 10% commission = 1100 -- the 20% markup never enters the vendor's number.
        assertThat(orders.get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(1100).setScale(2));
    }
}
