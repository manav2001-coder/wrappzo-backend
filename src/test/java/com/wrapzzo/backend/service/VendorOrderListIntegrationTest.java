package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.list.InvalidListQueryException;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vendor's Orders list, counted and filtered by stage in the database -- the same stages the portal has always derived from an order's
 * status and its artwork loop (lib/vendor-order-stage.ts) -- and by the division the order was raised in.
 */
@SpringBootTest
@Transactional
class VendorOrderListIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
    @Autowired private OrderService orderService;
    @Autowired private FeatureService featureService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderDesignRepository orderDesignRepository;

    private User mrA;
    private User mrB;
    private Vendor printer;
    private Vendor otherPrinter;
    private Gift withArtwork;
    private Gift withoutArtwork;
    private OrgUnit divisionA;
    private OrgUnit divisionB;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("Vendor List Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        Role member = roleRepository.save(new Role(company, "Member"));
        divisionA = orgUnitRepository.save(new OrgUnit(company, null, "Division A", "Division"));
        divisionB = orgUnitRepository.save(new OrgUnit(company, null, "Division B", "Division"));
        OrgUnit areaA = orgUnitRepository.save(new OrgUnit(company, divisionA, "Area A1", "Area"));
        mrA = user(company, member, "MR A", areaA);
        mrB = user(company, member, "MR B", divisionB);
        printer = vendorRepository.save(new Vendor("Printers", "v-" + UUID.randomUUID() + "@test.com", null, "n/a", mrA));
        otherPrinter = vendorRepository.save(new Vendor("Other Printers", "o-" + UUID.randomUUID() + "@test.com", null, "n/a", mrA));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        withArtwork = gift("Pad", printer, true);
        withoutArtwork = gift("Calendar", printer, false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User user(Company company, Role role, String name, OrgUnit unit) {
        User user = new User(company, role, name, "vl-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private Gift gift(String name, Vendor vendor, boolean artwork) {
        Gift gift = new Gift(name + " " + UUID.randomUUID(), "Stationery", "x", null, 7, vendor);
        gift.setModules(EnumSet.allOf(GiftModule.class));
        gift.setPrice(BigDecimal.valueOf(40));
        gift.setArtworkRequired(artwork);
        return giftRepository.save(gift);
    }

    private Order order(User by, Gift gift) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(by.getId(), null, List.of()));
        UUID id = quickGiftService.create(new CreateQuickGiftRequest(gift.getId(), null, "Dr " + UUID.randomUUID(), "98000", "PC-" + UUID.randomUUID(), null, "Road", null,
                null, null, null, new DoctorDetails("MBBS", "Cardiology", "Clinic"), 1)).orderId();
        return orderRepository.findById(id).orElseThrow();
    }

    private void proof(Order order, int version, OrderDesignStatus status) {
        OrderDesign design = new OrderDesign(order, version, "designs/" + UUID.randomUUID() + ".pdf", "v" + version + ".pdf", printer);
        design.setStatus(status);
        orderDesignRepository.save(design);
    }

    /** Twelve orders for the vendor (plus one for another vendor), one or more at every stage; eight in division A and four in division B. */
    private void seed() {
        order(mrA, withArtwork); // artwork: no proof yet
        proof(order(mrA, withArtwork), 1, OrderDesignStatus.PENDING_REVIEW); // review
        proof(order(mrA, withArtwork), 1, OrderDesignStatus.PENDING_FINAL_APPROVAL); // review
        proof(order(mrA, withArtwork), 1, OrderDesignStatus.REJECTED); // rework
        Order resubmitted = order(mrA, withArtwork); // review: the newest proof is the one that counts
        proof(resubmitted, 1, OrderDesignStatus.REJECTED);
        proof(resubmitted, 2, OrderDesignStatus.PENDING_REVIEW);
        proof(order(mrA, withArtwork), 1, OrderDesignStatus.APPROVED); // ready
        order(mrA, withoutArtwork); // ready: needs no proof
        Order approvedThenRejected = order(mrA, withArtwork); // ready: an approved proof is on file
        proof(approvedThenRejected, 1, OrderDesignStatus.APPROVED);
        proof(approvedThenRejected, 2, OrderDesignStatus.REJECTED);
        setStatus(order(mrB, withoutArtwork), OrderStatus.DISPATCHED); // dispatched
        setStatus(order(mrB, withoutArtwork), OrderStatus.RECEIVED_AT_WAREHOUSE); // warehouse
        setStatus(order(mrB, withoutArtwork), OrderStatus.DELIVERED); // delivered
        setStatus(order(mrB, withoutArtwork), OrderStatus.CANCELLED); // cancelled
        // Another vendor's order is never in this vendor's list.
        Order elsewhere = order(mrA, withoutArtwork);
        elsewhere.setAssignedVendor(otherPrinter);
        orderRepository.save(elsewhere);
    }

    private void setStatus(Order order, OrderStatus status) {
        order.setStatus(status);
        orderRepository.save(order);
    }

    private ListQueryParams stage(String stage) {
        return new ListQueryParams(0, 100, null, null, null, "[{\"field\":\"stage\",\"operator\":\"equals\",\"value\":\"" + stage + "\"}]");
    }

    @Test
    void everyOrderIsAtExactlyOneStageAndTheCountsSayHowMany() {
        seed();

        var summary = orderService.summaryForVendor(printer.getId(), null);

        assertThat(summary.total()).isEqualTo(12); // the other vendor's order is not counted
        assertThat(summary.byStage()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "artwork", 1L, "review", 3L, "rework", 1L, "ready", 3L, "dispatched", 1L, "warehouse", 1L, "delivered", 1L, "cancelled", 1L));
        assertThat(summary.byStage().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(summary.total());
    }

    @Test
    void theListNarrowsToAStage() {
        seed();

        assertThat(orderService.pageForVendor(printer.getId(), stage("review").toQuery()).total()).isEqualTo(3);
        assertThat(orderService.pageForVendor(printer.getId(), stage("ready").toQuery()).total()).isEqualTo(3);
        assertThat(orderService.pageForVendor(printer.getId(), stage("rework").toQuery()).data()).hasSize(1);
        assertThat(orderService.pageForVendor(printer.getId(), new ListQueryParams(0, 100, null, null, null,
                "[{\"field\":\"stage\",\"operator\":\"in\",\"value\":[\"artwork\",\"rework\"]}]").toQuery()).total()).isEqualTo(2);
        assertThatThrownBy(() -> orderService.pageForVendor(printer.getId(), stage("lost").toQuery())).isInstanceOf(InvalidListQueryException.class)
                .hasMessageContaining("not a value of 'stage'");
    }

    @Test
    void theDivisionFilterAndCountsFollowTheDivisionTheOrderWasRaisedIn() {
        seed();

        var inA = orderService.summaryForVendor(printer.getId(), divisionA.getId());
        assertThat(inA.total()).isEqualTo(8);
        var inB = orderService.summaryForVendor(printer.getId(), divisionB.getId());
        assertThat(inB.total()).isEqualTo(4);
        assertThat(inB.byStage()).containsEntry("dispatched", 1L).containsEntry("delivered", 1L).containsEntry("review", 0L);

        var filtered = orderService.pageForVendor(printer.getId(), new ListQueryParams(0, 100, null, null, null,
                "[{\"field\":\"divisionId\",\"operator\":\"equals\",\"value\":\"" + divisionB.getId() + "\"}]").toQuery());
        assertThat(filtered.total()).isEqualTo(4);
        assertThat(filtered.data()).extracting(VendorOrderResponse::divisionName).containsOnly("Division B");

        assertThat(orderService.orderDivisionsForVendor(printer.getId())).extracting(d -> d.name() + ":" + d.count()).containsExactly("Division A:8", "Division B:4");
    }

    @Test
    void searchAndSortWork() {
        seed();
        assertThat(orderService.pageForVendor(printer.getId(), new ListQueryParams(0, 100, "calendar", null, null, null).toQuery()).total()).isEqualTo(5); // 1 ready + 4 in B
        assertThat(orderService.pageForVendor(printer.getId(), new ListQueryParams(0, 3, null, "giftName", "asc", null).toQuery()).data()).hasSize(3);
    }
}
