package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.dto.RecurringOrderPlanResponse;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.dto.WarehouseOrderResponse;
import com.wrapzzo.backend.dto.WarehouseRequest;
import com.wrapzzo.backend.dto.WarehouseResponse;
import com.wrapzzo.backend.exception.InvalidOrderStatusTransitionException;
import com.wrapzzo.backend.exception.RecurringOrderPlanNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.RecurringPlanStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.RecurringOrderPlanRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
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

/**
 * The warehouse leg (vendor -> company warehouse -> doctor) and recurring monthly orders.
 *
 * Org tree: Division -> North (MR North). A company-wide warehouse plus one for North.
 */
@SpringBootTest
@Transactional
class WarehouseAndRecurringOrderIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
    @Autowired private OrderService orderService;
    @Autowired private WarehouseService warehouseService;
    @Autowired private RecurringOrderService recurringOrderService;
    @Autowired private CompanyFeatureFlagService companyFeatureFlagService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private QuickGiftRequestRepository quickGiftRequestRepository;
    @Autowired private RecurringOrderPlanRepository recurringOrderPlanRepository;

    private Company company;
    private OrgUnit north;
    private User mrNorth;
    private User otherMr;
    private User warehouseAdmin;
    private Vendor printer;
    private Gift pad;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Warehouse Test Pharma " + UUID.randomUUID()));
        Role member = roleRepository.save(new Role(company, "Member"));
        Role admin = roleRepository.save(new Role(company, "Warehouse Admin"));
        grant(admin, "warehouse.view");
        grant(admin, "warehouse.setup");
        grant(admin, "warehouse.operate");

        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        north = orgUnitRepository.save(new OrgUnit(company, division, "North", "Zone"));

        mrNorth = saveUser("MR North", member, north);
        otherMr = saveUser("Other MR", member, north);
        warehouseAdmin = saveUser("Ops", admin, null);
        warehouseAdmin.setCompanyWide(true);
        warehouseAdmin = userRepository.save(warehouseAdmin);

        printer = vendorRepository.save(new Vendor("Pad Printers", "pads-" + UUID.randomUUID() + "@test.com", null, "n/a", warehouseAdmin));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        pad = new Gift("Rx Pad " + UUID.randomUUID(), "Stationery", null, null, null, printer);
        pad.setModules(EnumSet.allOf(GiftModule.class));
        pad.setPrice(BigDecimal.TEN);
        pad = giftRepository.save(pad);
        companyFeatureFlagService.setEnabled(company.getId(), CompanyFeature.DIRECT_VENDOR_ORDERING, true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void grant(Role role, String key) {
        Template template = role.getTemplates().stream().findFirst().orElseGet(() -> {
            Template created = templateRepository.save(new Template(role.getCompany(), role.getName() + " access " + java.util.UUID.randomUUID(), null));
            role.getTemplates().add(created);
            roleRepository.save(role);
            return created;
        });
        template.getAccessControls().add(accessControlRepository.findByKey(key).orElseThrow());
        templateRepository.save(template);
    }

    private User saveUser(String name, Role role, OrgUnit unit) {
        User user = new User(company, role, name, "wh-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private CreateQuickGiftRequest padRequest(String doctor) {
        return new CreateQuickGiftRequest(pad.getId(), null, doctor, "98000" + (int) (Math.random() * 100000), null, "Clinic Road", null,
                null, null, null, null, 200);
    }

    private UUID placeOrder() {
        actAs(mrNorth);
        return quickGiftService.create(padRequest("Dr. Rao")).orderId();
    }

    private void setUpWarehouses() {
        companyFeatureFlagService.setEnabled(company.getId(), CompanyFeature.WAREHOUSE_LOGISTICS, true);
        actAs(warehouseAdmin);
        warehouseService.create(new WarehouseRequest("Central Warehouse", "Bhiwandi", null, null, null));
        warehouseService.create(new WarehouseRequest("North Warehouse", "Delhi", null, north.getId(), null));
    }

    @Test
    void withTheFlagOffTheVendorShipsStraightToTheDoctor() {
        UUID orderId = placeOrder();

        assertThat(orderRepository.findById(orderId).orElseThrow().getWarehouse()).isNull();
        VendorOrderResponse vendorView = orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DISPATCHED);
        assertThat(vendorView.shipToWarehouseName()).isNull();
        assertThat(orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DELIVERED).status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void anOrderGoesToTheWarehouseNearestItsOrgUnitAndTheWarehouseDeliversIt() {
        setUpWarehouses();
        UUID orderId = placeOrder();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getWarehouse().getName()).isEqualTo("North Warehouse");
        VendorOrderResponse vendorView = orderService.getForVendor(orderId, printer.getId());
        assertThat(vendorView.shipToWarehouseAddress()).isEqualTo("Delhi");
        assertThat(vendorView.deliveryAddress()).isEqualTo("Clinic Road");

        // The vendor's job ends at dispatch to the warehouse.
        orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DISPATCHED);
        assertThatThrownBy(() -> orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        actAs(warehouseAdmin);
        assertThat(warehouseService.listOrders()).extracting(WarehouseOrderResponse::id).contains(orderId);
        // No skipping a stage.
        assertThatThrownBy(() -> warehouseService.advance(orderId, OrderStatus.OUT_FOR_DELIVERY))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        warehouseService.advance(orderId, OrderStatus.RECEIVED_AT_WAREHOUSE);
        warehouseService.advance(orderId, OrderStatus.OUT_FOR_DELIVERY);
        WarehouseOrderResponse delivered = warehouseService.advance(orderId, OrderStatus.DELIVERED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.deliveryAddress()).isEqualTo("Clinic Road");

        // Once it is in the warehouse's hands, the vendor can no longer move it.
        assertThatThrownBy(() -> orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DISPATCHED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void anOrderFromAUnitWithNoWarehouseOfItsOwnFallsBackToTheCompanyWideOne() {
        companyFeatureFlagService.setEnabled(company.getId(), CompanyFeature.WAREHOUSE_LOGISTICS, true);
        actAs(warehouseAdmin);
        WarehouseResponse central = warehouseService.create(new WarehouseRequest("Central Warehouse", "Bhiwandi", null, null, null));
        warehouseService.create(new WarehouseRequest("Old North", "Delhi", null, north.getId(), false));

        UUID orderId = placeOrder();

        assertThat(orderRepository.findById(orderId).orElseThrow().getWarehouse().getId()).isEqualTo(central.id());
    }

    @Test
    void aRecurringPlanPlacesTheFirstOrderNowAndTheNextOnDemand() {
        actAs(mrNorth);
        RecurringOrderPlanResponse plan = recurringOrderService.create(padRequest("Dr. Monthly"));

        assertThat(plan.runsCompleted()).isEqualTo(1);
        assertThat(plan.nextRunOn()).isEqualTo(LocalDate.now().plusMonths(1));
        assertThat(quickGiftService.listMine()).hasSize(1);

        RecurringOrderPlanResponse after = recurringOrderService.placeNextNow(plan.id());
        assertThat(after.runsCompleted()).isEqualTo(2);
        assertThat(after.nextRunOn()).isEqualTo(LocalDate.now().plusMonths(2));
        List<QuickGiftRequestResponse> mine = quickGiftService.listMine();
        assertThat(mine).hasSize(2).allSatisfy(r -> {
            assertThat(r.recipientName()).isEqualTo("Dr. Monthly");
            assertThat(r.quantity()).isEqualTo(200);
            assertThat(r.orderId()).isNotNull();
        });
    }

    @Test
    void theScheduledRunPlacesADuePlanAsItsRequester() {
        actAs(mrNorth);
        RecurringOrderPlanResponse plan = recurringOrderService.create(padRequest("Dr. Due"));
        var entity = recurringOrderPlanRepository.findById(plan.id()).orElseThrow();
        entity.setNextRunOn(LocalDate.now());
        recurringOrderPlanRepository.saveAndFlush(entity);
        SecurityContextHolder.clearContext();

        assertThat(recurringOrderService.findDue(LocalDate.now())).extracting(RecurringOrderService.DuePlan::planId).contains(plan.id());
        actAs(mrNorth);
        recurringOrderService.placeDue(plan.id());

        assertThat(quickGiftRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(mrNorth.getId())).hasSize(2);
        assertThat(recurringOrderPlanRepository.findById(plan.id()).orElseThrow().getNextRunOn()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void aPausedPlanIsNotDueAndOnlyItsRequesterCanChangeIt() {
        actAs(mrNorth);
        RecurringOrderPlanResponse plan = recurringOrderService.create(padRequest("Dr. Paused"));
        assertThat(recurringOrderService.pause(plan.id()).status()).isEqualTo(RecurringPlanStatus.PAUSED);
        assertThat(recurringOrderService.findDue(LocalDate.now().plusMonths(2)))
                .extracting(RecurringOrderService.DuePlan::planId)
                .doesNotContain(plan.id());

        actAs(otherMr);
        assertThat(recurringOrderService.listMine()).isEmpty();
        assertThatThrownBy(() -> recurringOrderService.cancel(plan.id())).isInstanceOf(RecurringOrderPlanNotFoundException.class);

        actAs(mrNorth);
        assertThat(recurringOrderService.cancel(plan.id()).status()).isEqualTo(RecurringPlanStatus.CANCELLED);
        assertThatThrownBy(() -> recurringOrderService.placeNextNow(plan.id())).isInstanceOf(RecurringOrderPlanNotFoundException.class);
    }
}
