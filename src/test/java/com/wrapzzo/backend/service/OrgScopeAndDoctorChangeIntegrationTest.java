package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateDoctorChangeRequest;
import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DashboardResponse;
import com.wrapzzo.backend.dto.DoctorChangeRequestResponse;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.DoctorChangeAction;
import com.wrapzzo.backend.model.DoctorChangeStatus;
import com.wrapzzo.backend.exception.QuantityBelowMinimumException;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Recipient;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RecipientAssignmentRepository;
import com.wrapzzo.backend.repository.RecipientRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The functional gaps closed after the prescription-pad work: a manager covering more than one
 * zone, the dashboard staying inside the viewer's org scope, and doctor change requests carrying
 * the print details (and finding an existing doctor by registration number instead of
 * duplicating them).
 *
 * Org tree: Division -> North (MR North), South (MR South).
 */
@SpringBootTest
@Transactional
class OrgScopeAndDoctorChangeIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
    @Autowired private OrderService orderService;
    @Autowired private DashboardService dashboardService;
    @Autowired private UserService userService;
    @Autowired private DoctorChangeRequestService doctorChangeRequestService;
    @Autowired private AuthService authService;
    @Autowired private CompanyFeatureFlagService companyFeatureFlagService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private FeatureService featureService;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private RecipientRepository recipientRepository;
    @Autowired private RecipientAssignmentRepository recipientAssignmentRepository;

    private Company company;
    private Role managerRole;
    private OrgUnit north;
    private OrgUnit south;
    private User mrNorth;
    private User mrSouth;
    private User manager;
    private User platformAdmin;
    private Gift pad;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Scope Test Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        Role member = roleRepository.save(new Role(company, "Member"));
        managerRole = roleRepository.save(new Role(company, "Zonal Manager"));
        grant(managerRole, "order.view");
        grant(managerRole, "recipient.view");

        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        north = orgUnitRepository.save(new OrgUnit(company, division, "North", "Zone"));
        south = orgUnitRepository.save(new OrgUnit(company, division, "South", "Zone"));

        manager = saveUser("Manager", managerRole, north);
        mrNorth = saveUser("MR North", member, north);
        mrSouth = saveUser("MR South", member, south);
        mrNorth.setReportsTo(manager);
        userRepository.save(mrNorth);

        Role platformRole = roleRepository.save(new Role(company, "Admin for test"));
        grant(platformRole, "user.view");
        grant(platformRole, "user.edit");
        platformAdmin = saveUser("Head Office", platformRole, null);
        platformAdmin.setCompanyWide(true);
        userRepository.save(platformAdmin);

        Vendor printer = vendorRepository.save(new Vendor("Pad Printers", "pads-" + UUID.randomUUID() + "@test.com", null, "n/a", manager));
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
        User user = new User(company, role, name, "scope-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private UUID placeOrder(User mr) {
        actAs(mr);
        return quickGiftService.create(new CreateQuickGiftRequest(
                pad.getId(), null, "Dr. " + mr.getName(), "98000" + (int) (Math.random() * 100000), null, "Somewhere", null, null, null, null,
                null, 1)).orderId();
    }

    @Test
    void aManagerSeesAnExtraZoneOnlyOnceItIsAssigned() {
        UUID northOrder = placeOrder(mrNorth);
        UUID southOrder = placeOrder(mrSouth);

        actAs(manager);
        assertThat(orderService.list()).extracting(OrderResponse::id).contains(northOrder).doesNotContain(southOrder);

        actAs(platformAdmin);
        UserResponse updated = userService.setExtraOrgUnits(company.getId(), manager.getId(), List.of(south.getId(), north.getId()));
        // Their own unit is never stored as an extra one.
        assertThat(updated.extraOrgUnits()).extracting(UserResponse.OrgUnitRef::name).containsExactly("South");

        actAs(manager);
        assertThat(orderService.list()).extracting(OrderResponse::id).contains(northOrder, southOrder);
    }

    @Test
    void theDashboardStaysInsideTheViewersOrgScope() {
        UUID northOrder = placeOrder(mrNorth);
        UUID southOrder = placeOrder(mrSouth);

        actAs(manager);
        DashboardResponse dashboard = dashboardService.summary();
        assertThat(dashboard.recentOrders()).extracting(DashboardResponse.OrderTimelineItem::id).contains(northOrder).doesNotContain(southOrder);
    }

    @Test
    void anApprovedDoctorAdditionCarriesThePrintDetails() {
        actAs(mrNorth);
        DoctorChangeRequestResponse request = doctorChangeRequestService.create(new CreateDoctorChangeRequest(
                DoctorChangeAction.ADD, null, "Dr. Meera Iyer", "9800000003", null, "Clinic Road", null,
                new DoctorDetails("MBBS, DNB", "TN-555", "Paediatrics", "Iyer Child Care")));
        assertThat(request.proposedDoctor().registrationNumber()).isEqualTo("TN-555");

        actAs(manager);
        DoctorChangeRequestResponse approved = doctorChangeRequestService.approve(request.id());

        Recipient doctor = recipientRepository.findById(approved.recipientId()).orElseThrow();
        assertThat(doctor.getRegistrationNumber()).isEqualTo("TN-555");
        assertThat(doctor.getSpeciality()).isEqualTo("Paediatrics");
        assertThat(recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(doctor.getId(), mrNorth.getId())).isTrue();
    }

    @Test
    void addingADoctorWhoseRegistrationIsKnownAssignsTheExistingOne() {
        Recipient existing = new Recipient(company, "Dr. A. Shah", "9800000001", null, null, null, mrSouth);
        existing.setRegistrationNumber("MH-12345");
        existing = recipientRepository.save(existing);

        actAs(mrNorth);
        DoctorChangeRequestResponse request = doctorChangeRequestService.create(new CreateDoctorChangeRequest(
                DoctorChangeAction.ADD, null, "Asha Shah", "9800000099", null, null, null, new DoctorDetails(null, "mh-12345", null, null)));

        actAs(manager);
        DoctorChangeRequestResponse approved = doctorChangeRequestService.approve(request.id());

        assertThat(approved.recipientId()).isEqualTo(existing.getId());
        assertThat(recipientRepository.findByCompanyIdOrderByNameAsc(company.getId())).hasSize(1);
        assertThat(recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(existing.getId(), mrNorth.getId())).isTrue();
    }

    private DoctorChangeRequestResponse requestNewDoctor(User mr, String registration) {
        actAs(mr);
        return doctorChangeRequestService.create(new CreateDoctorChangeRequest(
                DoctorChangeAction.ADD, null, "Dr. New", "9800000077", null, "Clinic Road", null,
                new DoctorDetails(null, registration, null, null)));
    }

    @Test
    void aPeerMrCannotReviewAnotherMrsDoctorRequest() {
        // Every real Member manages their own list -- it must not make them a reviewer.
        grant(mrSouth.getRole(), "recipient_list.manage");
        DoctorChangeRequestResponse request = requestNewDoctor(mrNorth, "PEER-1");

        actAs(mrSouth);
        assertThat(doctorChangeRequestService.listPendingForReviewer()).isEmpty();
        assertThatThrownBy(() -> doctorChangeRequestService.approve(request.id())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recipientApproveReviewsCompanyWideButNeverYourOwnRequest() {
        grant(platformAdmin.getRole(), DoctorChangeRequestService.APPROVE_ANY_KEY);
        DoctorChangeRequestResponse fromMr = requestNewDoctor(mrSouth, "ANY-1");
        DoctorChangeRequestResponse ownRequest = requestNewDoctor(platformAdmin, "ANY-2");

        actAs(platformAdmin);
        assertThat(doctorChangeRequestService.listPendingForReviewer()).extracting(DoctorChangeRequestResponse::id)
                .contains(fromMr.id())
                .doesNotContain(ownRequest.id());
        assertThatThrownBy(() -> doctorChangeRequestService.approve(ownRequest.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(doctorChangeRequestService.approve(fromMr.id()).status()).isEqualTo(DoctorChangeStatus.APPROVED);
    }

    @Test
    void anOrderBelowTheGiftsMinimumIsRefused() {
        pad.setMoq(50);
        pad = giftRepository.save(pad);

        actAs(mrNorth);
        assertThatThrownBy(() -> quickGiftService.create(new CreateQuickGiftRequest(
                pad.getId(), null, "Dr. Few", "9800000055", null, "Somewhere", null, null, null, null, null, 5)))
                .isInstanceOf(QuantityBelowMinimumException.class);
        assertThat(quickGiftService.create(new CreateQuickGiftRequest(
                pad.getId(), null, "Dr. Enough", "9800000056", null, "Somewhere", null, null, null, null, null, 50)).orderId())
                .isNotNull();
    }

    @Test
    void aManagerWithReportsIsFlaggedAsManagingATeam() {
        actAs(manager);
        assertThat(authService.getCurrentUser().managesTeam()).isTrue();
        actAs(mrNorth);
        assertThat(authService.getCurrentUser().managesTeam()).isFalse();
    }
}
