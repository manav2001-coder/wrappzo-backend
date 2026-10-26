package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.exception.ArtworkNotApprovedException;
import com.wrapzzo.backend.exception.GiftNotAvailableForQuickGiftException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.exception.QuickGiftRequestNotFoundException;
import com.wrapzzo.backend.exception.RecipientAlreadyExistsException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.RoleAccessControl;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
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
 * Alembic's prescription-pad flow end to end at the service layer: an MR orders pads for a
 * doctor straight from a vendor (DIRECT_VENDOR_ORDERING), the order is visible along the org
 * tree only, and an artwork-required order cannot leave the vendor before a proof is approved.
 *
 * Org tree: Division -> Zone North -> Area N1 (the MR), and Zone South -> Area S1 (another MR).
 */
@SpringBootTest
@Transactional
class PrescriptionPadOrderingIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
    @Autowired private OrderService orderService;
    @Autowired private RecipientService recipientService;
    @Autowired private CompanyFeatureFlagService companyFeatureFlagService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RoleAccessControlRepository roleAccessControlRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderDesignRepository orderDesignRepository;

    private Company company;
    private Role memberRole;
    private OrgUnit zoneNorth;
    private User mrNorth;
    private User mrSouth;
    private User zonalManagerNorth;
    private Vendor printer;
    private Gift pad;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Pad Test Pharma " + UUID.randomUUID()));
        memberRole = roleRepository.save(new Role(company, "Member"));

        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        zoneNorth = orgUnitRepository.save(new OrgUnit(company, division, "North", "Zone"));
        OrgUnit zoneSouth = orgUnitRepository.save(new OrgUnit(company, division, "South", "Zone"));
        OrgUnit areaN1 = orgUnitRepository.save(new OrgUnit(company, zoneNorth, "N1", "Area"));
        OrgUnit areaS1 = orgUnitRepository.save(new OrgUnit(company, zoneSouth, "S1", "Area"));

        mrNorth = saveUser("MR North", areaN1);
        mrSouth = saveUser("MR South", areaS1);
        zonalManagerNorth = saveUser("ZM North", zoneNorth);

        printer = vendorRepository.save(new Vendor("Pad Printers", "pads-" + UUID.randomUUID() + "@test.com", null, "n/a", mrNorth));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        pad = savePad(printer, true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User saveUser(String name, OrgUnit unit) {
        User user = new User(company, memberRole, name, "pad-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private Gift savePad(Vendor vendor, boolean artworkRequired) {
        Gift gift = new Gift("Prescription Pad " + UUID.randomUUID(), "Stationery", "A5, 100 leaves", null, 7, vendor);
        gift.setModules(EnumSet.allOf(GiftModule.class));
        gift.setPrice(BigDecimal.valueOf(40));
        gift.setArtworkRequired(artworkRequired);
        return giftRepository.save(gift);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void actAsVendor(Vendor vendor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(vendor.getId(), null, List.of()));
    }

    private CreateQuickGiftRequest padOrder(Gift gift, int quantity) {
        return new CreateQuickGiftRequest(
                gift.getId(), null, "Dr. Asha Shah", "9800000001", null, "12 MG Road, Pune", null, null, null, null,
                new DoctorDetails("MBBS, MD", "MH-12345", "Cardiology", "Shah Heart Clinic"), quantity);
    }

    private void enableDirectOrdering() {
        companyFeatureFlagService.setEnabled(company.getId(), CompanyFeature.DIRECT_VENDOR_ORDERING, true);
    }

    @Test
    void directOrderingGoesStraightToTheChosenVendorWithQuantityAndPrintDetails() {
        enableDirectOrdering();
        actAs(mrNorth);

        QuickGiftRequestResponse placed = quickGiftService.create(padOrder(pad, 5));

        assertThat(placed.status()).isEqualTo(QuickGiftStatus.APPROVED);
        assertThat(placed.orderId()).isNotNull();
        assertThat(placed.quantity()).isEqualTo(5);
        assertThat(placed.doctor().registrationNumber()).isEqualTo("MH-12345");

        Order order = orderRepository.findById(placed.orderId()).orElseThrow();
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PRODUCTION_QUEUE);
        assertThat(order.getQuantity()).isEqualTo(5);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("200.00");
        assertThat(order.getOrgUnit().getId()).isEqualTo(mrNorth.getOrgUnit().getId());

        actAsVendor(printer);
        VendorOrderResponse vendorView = orderService.getForVendor(order.getId(), printer.getId());
        assertThat(vendorView.quantity()).isEqualTo(5);
        assertThat(vendorView.deliveryAddress()).isEqualTo("12 MG Road, Pune");
        assertThat(vendorView.doctor().clinicName()).isEqualTo("Shah Heart Clinic");
        assertThat(vendorView.artworkRequired()).isTrue();
        assertThat(vendorView.artworkApproved()).isFalse();
    }

    @Test
    void withoutTheFlagARequestStillWaitsForApproval() {
        actAs(mrNorth);

        QuickGiftRequestResponse placed = quickGiftService.create(padOrder(pad, 2));

        assertThat(placed.status()).isEqualTo(QuickGiftStatus.SUBMITTED);
        assertThat(placed.orderId()).isNull();
    }

    @Test
    void aGiftOutsideTheRequestersVendorRosterCannotBeOrdered() {
        Vendor stranger = vendorRepository.save(new Vendor("Not Ours", "stranger-" + UUID.randomUUID() + "@test.com", null, "n/a", mrNorth));
        Gift foreignPad = savePad(stranger, false);
        actAs(mrNorth);

        assertThatThrownBy(() -> quickGiftService.create(padOrder(foreignPad, 1)))
                .isInstanceOf(GiftNotAvailableForQuickGiftException.class);
    }

    @Test
    void anArtworkRequiredOrderCannotBeDispatchedUntilAProofIsApproved() {
        enableDirectOrdering();
        actAs(mrNorth);
        UUID orderId = quickGiftService.create(padOrder(pad, 1)).orderId();
        Order order = orderRepository.findById(orderId).orElseThrow();

        actAsVendor(printer);
        assertThatThrownBy(() -> orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DISPATCHED))
                .isInstanceOf(ArtworkNotApprovedException.class);

        OrderDesign rejected = new OrderDesign(order, 1, "designs/v1.pdf", "v1.pdf", printer);
        rejected.setStatus(OrderDesignStatus.REJECTED);
        orderDesignRepository.save(rejected);
        assertThatThrownBy(() -> orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DELIVERED))
                .isInstanceOf(ArtworkNotApprovedException.class);

        OrderDesign approved = new OrderDesign(order, 2, "designs/v2.pdf", "v2.pdf", printer);
        approved.setStatus(OrderDesignStatus.APPROVED);
        orderDesignRepository.save(approved);
        assertThat(orderService.updateStatusForVendor(orderId, printer.getId(), OrderStatus.DISPATCHED).status())
                .isEqualTo(OrderStatus.DISPATCHED);
    }

    @Test
    void theVendorQueueCarriesEachOrdersLatestProofAndItsReviewComment() {
        enableDirectOrdering();
        actAs(mrNorth);
        UUID orderId = quickGiftService.create(padOrder(pad, 2)).orderId();
        Order order = orderRepository.findById(orderId).orElseThrow();

        actAsVendor(printer);
        VendorOrderResponse before = orderService.listForVendor(printer.getId()).stream().filter(o -> o.id().equals(orderId)).findFirst().orElseThrow();
        assertThat(before.latestDesignStatus()).isNull();
        assertThat(before.designVersions()).isZero();

        OrderDesign rejected = new OrderDesign(order, 1, "designs/v1.pdf", "v1.pdf", printer);
        rejected.setStatus(OrderDesignStatus.REJECTED);
        rejected.setReviewComment("Registration number is cut off");
        orderDesignRepository.save(rejected);
        orderDesignRepository.save(new OrderDesign(order, 2, "designs/v2.pdf", "v2.pdf", printer));

        VendorOrderResponse after = orderService.listForVendor(printer.getId()).stream().filter(o -> o.id().equals(orderId)).findFirst().orElseThrow();
        assertThat(after.designVersions()).isEqualTo(2);
        assertThat(after.latestDesignStatus()).isEqualTo(OrderDesignStatus.PENDING_REVIEW);
        assertThat(after.artworkApproved()).isFalse();
        assertThat(after.doctor().registrationNumber()).isEqualTo("MH-12345");

        VendorOrderResponse single = orderService.getForVendor(orderId, printer.getId());
        assertThat(single.designVersions()).isEqualTo(2);
        assertThat(single.latestDesignStatus()).isEqualTo(OrderDesignStatus.PENDING_REVIEW);
    }

    @Test
    void ordersAreVisibleUpTheOrgTreeButNotAcrossIt() {
        enableDirectOrdering();
        actAs(mrNorth);
        UUID northOrder = quickGiftService.create(padOrder(pad, 1)).orderId();

        actAs(zonalManagerNorth);
        assertThat(orderService.list()).extracting(OrderResponse::id).contains(northOrder);
        assertThat(orderService.get(northOrder).orgUnitName()).isEqualTo("N1");

        actAs(mrSouth);
        assertThat(orderService.list()).extracting(OrderResponse::id).doesNotContain(northOrder);
        assertThatThrownBy(() -> orderService.get(northOrder)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void orgScopeAllSeesEveryUnit() {
        enableDirectOrdering();
        actAs(mrNorth);
        UUID northOrder = quickGiftService.create(padOrder(pad, 1)).orderId();

        Role adminRole = roleRepository.save(new Role(company, "Company Admin"));
        roleAccessControlRepository.save(new RoleAccessControl(adminRole, accessControlRepository.findByKey(OrgScopeService.ALL_UNITS_KEY).orElseThrow()));
        User headOffice = userRepository.save(new User(company, adminRole, "Head Office", "ho-" + UUID.randomUUID() + "@test.com", "n/a"));

        actAs(headOffice);
        assertThat(orderService.list()).extracting(OrderResponse::id).contains(northOrder);
    }

    @Test
    void approvalQueueFollowsTheOrgTree() {
        actAs(mrNorth);
        UUID requestId = quickGiftService.create(padOrder(pad, 1)).id();

        actAs(mrSouth);
        assertThat(quickGiftService.listPendingApproval()).extracting(QuickGiftRequestResponse::id).doesNotContain(requestId);
        assertThatThrownBy(() -> quickGiftService.approve(requestId)).isInstanceOf(QuickGiftRequestNotFoundException.class);

        actAs(zonalManagerNorth);
        assertThat(quickGiftService.listPendingApproval()).extracting(QuickGiftRequestResponse::id).contains(requestId);
        assertThat(quickGiftService.approve(requestId).status()).isEqualTo(QuickGiftStatus.APPROVED);
    }

    @Test
    void aRegistrationNumberIdentifiesTheSameDoctor() {
        actAs(mrNorth);
        recipientService.create(new CreateRecipientRequest("Dr. A. Shah", "9800000001", null, null, null,
                new DoctorDetails("MBBS", "MH-777", null, null)));

        assertThatThrownBy(() -> recipientService.create(new CreateRecipientRequest("Asha Shah", "9800000002", null, null, null,
                new DoctorDetails(null, "mh-777", null, null))))
                .isInstanceOf(RecipientAlreadyExistsException.class);
    }
}
