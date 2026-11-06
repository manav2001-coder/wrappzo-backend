package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.BulkAssignResponse;
import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.OrderApprovalSettingsRequest;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidOrderApprovalSettingsException;
import com.wrapzzo.backend.model.ArtworkApprovalScope;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
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
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
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
 * Order approval: with it on, an order waits for the requester's RM and reaches the vendor only on the RM's yes; a no cancels it.
 * Org tree: Division -> Zone North -> Area N1 (the MR, reporting to the RM) and Area N2 (another MR, reporting to nobody).
 */
@SpringBootTest
@Transactional
class OrderApprovalIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
    @Autowired private OrderApprovalService orderApprovalService;
    @Autowired private OrderApprovalSettingsService settingsService;
    @Autowired private OrderService orderService;
    @Autowired private FeatureService featureService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;

    private Company company;
    private Role memberRole;
    private Role rmRole;
    private User mr;
    private User lonelyMr;
    private User rm;
    private User otherRm;
    private User divisionRm;
    private User ho;
    private Gift pad;
    private Vendor printer;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Approval Test Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        memberRole = roleRepository.save(new Role(company, "Member"));
        rmRole = roleRepository.save(new Role(company, "RM"));
        grant(rmRole, "approval.decide");

        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        OrgUnit zone = orgUnitRepository.save(new OrgUnit(company, division, "North", "Zone"));
        OrgUnit areaOne = orgUnitRepository.save(new OrgUnit(company, zone, "N1", "Area"));
        OrgUnit areaTwo = orgUnitRepository.save(new OrgUnit(company, zone, "N2", "Area"));

        rm = saveUser("RM North", rmRole, zone);
        otherRm = saveUser("RM Elsewhere", rmRole, zone);
        Role hoRole = roleRepository.save(new Role(company, "HO"));
        grant(hoRole, "approval.decide");
        grant(hoRole, "artwork.final_approve");
        ho = saveUser("HO", hoRole, division);
        divisionRm = saveUser("RM Division", rmRole, division);
        mr = saveUser("MR North", memberRole, areaOne);
        mr.setReportsTo(rm);
        lonelyMr = saveUser("MR Alone", memberRole, areaTwo);

        printer = vendorRepository.save(new Vendor("Pad Printers", "pads-" + UUID.randomUUID() + "@test.com", null, "n/a", mr));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        pad = new Gift("Prescription Pad " + UUID.randomUUID(), "Stationery", "A5, 100 leaves", null, 7, printer);
        pad.setModules(EnumSet.allOf(GiftModule.class));
        pad.setPrice(BigDecimal.valueOf(40));
        pad = giftRepository.save(pad);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void grant(Role role, String key) {
        Template template = templateRepository.save(new Template(role.getCompany(), role.getName() + " access " + UUID.randomUUID(), null));
        role.getTemplates().add(template);
        roleRepository.save(role);
        template.getAccessControls().add(accessControlRepository.findByKey(key).orElseThrow());
        templateRepository.save(template);
    }

    private User saveUser(String name, Role role, OrgUnit unit) {
        User user = new User(company, role, name, "ap-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void switchOn(ArtworkApprovalScope scope) {
        actAs(mr);
        settingsService.set(new OrderApprovalSettingsRequest(true, rmRole.getId(), scope));
    }

    private Order place(User requester) {
        actAs(requester);
        UUID orderId = quickGiftService.create(new CreateQuickGiftRequest(
                pad.getId(), null, "Dr. Asha Shah", "9800000001", "PC-ASHA", null, "12 MG Road, Pune", null, null, null, null,
                new DoctorDetails("MBBS, MD", "Cardiology", "Shah Heart Clinic"), 5)).orderId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    @Test
    void withApprovalOffTheOrderGoesStraightToTheVendor() {
        Order order = place(mr);

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.NOT_REQUIRED);
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PRODUCTION_QUEUE);
    }

    @Test
    void withApprovalOnTheOrderWaitsAwayFromEveryVendorUntilTheRmApproves() {
        switchOn(ArtworkApprovalScope.REPORTING_LINE);
        Order order = place(mr);

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.PENDING);
        assertThat(order.getAssignedVendor()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        // Only the RM above the requester sees it waiting -- not the requester, not another RM.
        actAs(mr);
        assertThat(orderApprovalService.listPending()).isEmpty();
        actAs(otherRm);
        assertThat(orderApprovalService.listPending()).isEmpty();
        actAs(rm);
        assertThat(orderApprovalService.listPending()).extracting(p -> p.orderId()).containsExactly(order.getId());
        // An HO holding Final Artwork Approval is not in this cycle: the order has its RM, so it is not theirs to see or decide.
        actAs(ho);
        assertThat(orderApprovalService.listPending()).isEmpty();
        assertThatThrownBy(() -> orderApprovalService.approve(order.getId(), null)).isInstanceOf(ConflictException.class).hasMessageContaining("not waiting on you");
        actAs(rm);

        // Platform staff cannot route it around the approver, one by one or in bulk.
        assertThatThrownBy(() -> orderService.autoAssignVendor(order.getId())).isInstanceOf(ConflictException.class).hasMessageContaining("waiting for its approver");
        assertThatThrownBy(() -> orderService.assignVendorManually(order.getId(), printer.getId())).isInstanceOf(ConflictException.class);
        BulkAssignResponse bulk = orderService.autoAssignQuickGift(company.getId());
        assertThat(bulk.assignedCount()).isZero();

        orderApprovalService.approve(order.getId(), "Looks right");

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.APPROVED);
        assertThat(order.getApprovalDecidedBy().getId()).isEqualTo(rm.getId());
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PRODUCTION_QUEUE);
        assertThat(orderApprovalService.listPending()).isEmpty();
    }

    @Test
    void anotherRmCannotDecideTheOrderAndTheRequesterCannotDecideTheirOwn() {
        switchOn(ArtworkApprovalScope.REPORTING_LINE);
        Order order = place(mr);

        actAs(otherRm);
        assertThatThrownBy(() -> orderApprovalService.approve(order.getId(), null)).isInstanceOf(ConflictException.class).hasMessageContaining("not waiting on you");
        actAs(mr);
        assertThatThrownBy(() -> orderApprovalService.approve(order.getId(), null)).isInstanceOf(ConflictException.class);
        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.PENDING);
    }

    @Test
    void turningTheOrderDownNeedsANoteAndCancelsItForGood() {
        switchOn(ArtworkApprovalScope.REPORTING_LINE);
        Order order = place(mr);

        actAs(rm);
        assertThatThrownBy(() -> orderApprovalService.reject(order.getId(), "  ")).isInstanceOf(ConflictException.class).hasMessageContaining("Say why");
        orderApprovalService.reject(order.getId(), "Wrong doctor");

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.REJECTED);
        assertThat(order.getApprovalComment()).isEqualTo("Wrong doctor");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getAssignedVendor()).isNull();
        // Decided once: a second decision is refused.
        assertThatThrownBy(() -> orderApprovalService.approve(order.getId(), null)).isInstanceOf(ConflictException.class).hasMessageContaining("already been decided");
    }

    @Test
    void divisionScopeLetsTheRmPlacedInTheDivisionDecide() {
        switchOn(ArtworkApprovalScope.DIVISION);
        Order order = place(mr);

        // Whoever holds the role in the requester's division (the top of their tree) may decide -- here the one placed in it.
        actAs(divisionRm);
        assertThat(orderApprovalService.listPending()).extracting(p -> p.orderId()).containsExactly(order.getId());
        actAs(otherRm);
        assertThat(orderApprovalService.listPending()).isEmpty();
        actAs(divisionRm);
        orderApprovalService.approve(order.getId(), null);
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
    }

    @Test
    void anRequesterWhoIsTheApproverRoleDoesNotWaitOnThemselves() {
        switchOn(ArtworkApprovalScope.REPORTING_LINE);
        Order order = place(rm);

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.NOT_REQUIRED);
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
    }

    @Test
    void anOrderWithNoRmAboveTheRequesterWaitsForTheHoToCover() {
        switchOn(ArtworkApprovalScope.REPORTING_LINE);
        Order order = place(lonelyMr);

        assertThat(order.getApprovalStatus()).isEqualTo(OrderApprovalStatus.PENDING);
        actAs(rm);
        assertThat(orderApprovalService.listPending()).isEmpty();
        // With no RM to decide it, the HO covers.
        actAs(ho);
        assertThat(orderApprovalService.listPending()).extracting(p -> p.orderId()).containsExactly(order.getId());
        orderApprovalService.approve(order.getId(), null);
        assertThat(order.getAssignedVendor().getId()).isEqualTo(printer.getId());
    }

    @Test
    void theSetupNeedsAnApproverToSwitchOnAndOnlyOneOfTheCompanysRoles() {
        actAs(mr);
        assertThatThrownBy(() -> settingsService.set(new OrderApprovalSettingsRequest(true, null, ArtworkApprovalScope.REPORTING_LINE)))
                .isInstanceOf(InvalidOrderApprovalSettingsException.class).hasMessageContaining("Pick who approves");

        Company other = companyRepository.save(new Company("Other " + UUID.randomUUID()));
        Role foreign = roleRepository.save(new Role(other, "RM"));
        assertThatThrownBy(() -> settingsService.set(new OrderApprovalSettingsRequest(true, foreign.getId(), ArtworkApprovalScope.REPORTING_LINE)))
                .isInstanceOf(InvalidOrderApprovalSettingsException.class);

        // Off needs no approver, and remembers the scope.
        var off = settingsService.set(new OrderApprovalSettingsRequest(false, null, ArtworkApprovalScope.DIVISION));
        assertThat(off.enabled()).isFalse();
        assertThat(off.scope()).isEqualTo(ArtworkApprovalScope.DIVISION);
        assertThat(off.roles()).extracting(r -> r.name()).contains("RM", "Member");
    }
}
