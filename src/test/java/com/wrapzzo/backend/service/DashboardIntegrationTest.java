package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DashboardDrilldownResponse;
import com.wrapzzo.backend.dto.DashboardResponse;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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
 * The dashboard's figures over a known set of orders: what each viewer's scope counts, the doctors counted once, the top lists, the
 * trend and the drill-downs. Written against the original in-memory implementation and kept green through the move to database totals.
 *
 * Six Quick Gift orders: three in Area North 1 (one doctor twice, one delivered), one in North 2 (delivered), two in South 1 (one dispatched, one cancelled).
 */
@SpringBootTest
@Transactional
class DashboardIntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private QuickGiftService quickGiftService;
    @Autowired private FeatureService featureService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CompanyVendorRepository companyVendorRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private com.wrapzzo.backend.repository.CampaignRepository campaignRepository;
    @Autowired private com.wrapzzo.backend.repository.SubmissionRepository submissionRepository;

    private User mrNorth1;
    private User mrNorth2;
    private User mrSouth;
    private User admin;
    private User zonalNorth;
    private Gift pad;
    private Gift calendar;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("Dash Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        Role member = roleRepository.save(new Role(company, "Member"));
        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        OrgUnit zoneNorth = orgUnitRepository.save(new OrgUnit(company, division, "Zone North", "Zone"));
        OrgUnit zoneSouth = orgUnitRepository.save(new OrgUnit(company, division, "Zone South", "Zone"));
        OrgUnit north1 = orgUnitRepository.save(new OrgUnit(company, zoneNorth, "North 1", "Area"));
        OrgUnit north2 = orgUnitRepository.save(new OrgUnit(company, zoneNorth, "North 2", "Area"));
        OrgUnit south1 = orgUnitRepository.save(new OrgUnit(company, zoneSouth, "South 1", "Area"));
        mrNorth1 = user(company, member, "MR North 1", north1);
        mrNorth2 = user(company, member, "MR North 2", north2);
        mrSouth = user(company, member, "MR South", south1);
        zonalNorth = user(company, member, "ZM North", zoneNorth);
        mrNorth1.setReportsTo(zonalNorth);
        mrNorth2.setReportsTo(zonalNorth);
        admin = user(company, member, "Admin", null);
        admin.setCompanyWide(true);
        Vendor printer = vendorRepository.save(new Vendor("Printers", "d-" + UUID.randomUUID() + "@test.com", null, "n/a", mrNorth1));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        pad = gift("Pad", printer);
        calendar = gift("Calendar", printer);

        order(mrNorth1, pad, "Dr A", "111", 5);
        order(mrNorth1, pad, "Dr A again", "(111)", 3); // the same doctor: same phone digits
        Order delivered = order(mrNorth1, calendar, "Dr B", "222", 2);
        Order deliveredNorth2 = order(mrNorth2, pad, "Dr C", "333", 10);
        Order dispatched = order(mrSouth, pad, "Dr D", "444", 7);
        Order cancelled = order(mrSouth, calendar, "Dr E", "555", 1);
        delivered.setStatus(OrderStatus.DELIVERED);
        deliveredNorth2.setStatus(OrderStatus.DELIVERED);
        dispatched.setStatus(OrderStatus.DISPATCHED);
        cancelled.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAllAndFlush(List.of(delivered, deliveredNorth2, dispatched, cancelled));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User user(Company company, Role role, String name, OrgUnit unit) {
        User user = new User(company, role, name, "dash-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private Gift gift(String name, Vendor vendor) {
        Gift gift = new Gift(name + " " + UUID.randomUUID(), "Stationery", "x", null, 7, vendor);
        gift.setModules(EnumSet.allOf(GiftModule.class));
        gift.setPrice(BigDecimal.valueOf(40));
        return giftRepository.save(gift);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private Order order(User by, Gift gift, String doctor, String phone, int quantity) {
        actAs(by);
        UUID id = quickGiftService.create(new CreateQuickGiftRequest(gift.getId(), null, doctor, phone, "PC-" + UUID.randomUUID(), null, "Road", null, null, null,
                null, new DoctorDetails("MBBS", "Cardiology", "Clinic"), quantity)).orderId();
        return orderRepository.findById(id).orElseThrow();
    }

    @Test
    void aCompanyWideViewerSeesEveryFigure() {
        actAs(admin);
        DashboardResponse summary = dashboardService.summary();

        assertThat(summary.companyWide()).isTrue();
        assertThat(summary.giftsInProgress()).isEqualTo(2);
        assertThat(summary.dispatched()).isEqualTo(1);
        assertThat(summary.delivered()).isEqualTo(2);
        assertThat(summary.pelsRequested()).isEqualTo(27); // everything but the cancelled order
        assertThat(summary.doctorsRequested()).isEqualTo(4); // A (twice), B, C, D
        assertThat(summary.pelsServed()).isEqualTo(12);
        assertThat(summary.doctorsServed()).isEqualTo(2);
        assertThat(summary.orderSourceBreakdown()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("QUICK_GIFT", 6L));
        assertThat(summary.quickGiftStatusBreakdown()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("APPROVED", 6L));
        assertThat(summary.quickGiftsThisMonth()).isEqualTo(6);
        assertThat(summary.quickGiftsPending()).isZero();
        assertThat(summary.submissionsPending()).isZero();
        assertThat(summary.topGifts()).extracting(g -> g.giftName() + ":" + g.orderCount()).containsExactly(pad.getName() + ":4", calendar.getName() + ":2");
        assertThat(summary.topDepartments()).extracting(d -> d.departmentName() + ":" + d.orderCount()).containsExactly("North 1:3", "South 1:2", "North 2:1");
        assertThat(summary.recentOrders()).hasSize(6);
        assertThat(summary.recentActivity()).hasSize(12);
        assertThat(summary.recentActivity()).extracting(a -> a.verb()).containsAnyOf("requested Quick Gift", "approved Quick Gift", "dispatched order", "delivered order");
        // Twelve weeks of trend, oldest first; this week holds the six requests.
        assertThat(summary.activityTrend()).hasSize(12);
        assertThat(summary.activityTrend().get(11).weekStart()).isEqualTo(LocalDate.now().with(DayOfWeek.MONDAY).toString());
        assertThat(summary.activityTrend().get(11).quickGiftRequests()).isEqualTo(6);
        assertThat(summary.activityTrend().stream().mapToLong(DashboardResponse.TrendPoint::quickGiftRequests).sum()).isEqualTo(6);
    }

    @Test
    void aFieldUserSeesOnlyTheirOwnNumbers() {
        actAs(mrNorth1);
        DashboardResponse summary = dashboardService.summary();

        assertThat(summary.companyWide()).isFalse();
        assertThat(summary.giftsInProgress()).isEqualTo(2);
        assertThat(summary.delivered()).isEqualTo(1);
        assertThat(summary.pelsRequested()).isEqualTo(10);
        assertThat(summary.doctorsRequested()).isEqualTo(2);
        assertThat(summary.pelsServed()).isEqualTo(2);
        assertThat(summary.quickGiftsThisMonth()).isEqualTo(3);
        assertThat(summary.topDepartments()).extracting(d -> d.departmentName() + ":" + d.orderCount()).containsExactly("North 1:3");
        assertThat(summary.recentOrders()).hasSize(3);
    }

    @Test
    void aManagerSeesTheirTeamsNumbersButNotAnotherZones() {
        actAs(zonalNorth);
        DashboardResponse summary = dashboardService.summary();

        assertThat(summary.companyWide()).isTrue();
        assertThat(summary.orderSourceBreakdown()).containsEntry("QUICK_GIFT", 4L); // North 1 (3) and North 2 (1), not South
        assertThat(summary.pelsRequested()).isEqualTo(20);
        assertThat(summary.delivered()).isEqualTo(2);
    }

    @Test
    void drilldownsListTheRecordsBehindEachFigure() {
        actAs(admin);

        assertThat(dashboardService.drilldown("orderSource", "QUICK_GIFT").rows()).hasSize(6);
        assertThat(dashboardService.drilldown("topGift", pad.getName()).rows()).hasSize(4);
        assertThat(dashboardService.drilldown("topDepartment", "North 1").rows()).hasSize(3);
        assertThat(dashboardService.drilldown("pipelineStage", "delivered").rows()).hasSize(2);
        assertThat(dashboardService.drilldown("pipelineStage", "inProgress").rows()).hasSize(2);
        assertThat(dashboardService.drilldown("pipelineStage", "dispatched").rows()).hasSize(1);
        assertThat(dashboardService.drilldown("quickGiftStatus", "APPROVED").rows()).hasSize(6);
        assertThat(dashboardService.drilldown("quickGiftsThisMonth", "").rows()).hasSize(6);
        assertThat(dashboardService.drilldown("week", LocalDate.now().with(DayOfWeek.MONDAY) + ":QUICK_GIFT").rows()).hasSize(6);

        DashboardDrilldownResponse requested = dashboardService.drilldown("pelsRequested", "");
        assertThat(requested.rows()).extracting(r -> r.quantity()).containsExactly(10L, 8L, 7L, 2L);
        assertThat(requested.rows().get(1).subtitle()).isEqualTo("2 orders");
        DashboardDrilldownResponse served = dashboardService.drilldown("pelsServed", "");
        assertThat(served.rows()).extracting(r -> r.quantity()).containsExactly(10L, 2L);
    }

    private void submission(Company company, com.wrapzzo.backend.model.Campaign campaign, User by, com.wrapzzo.backend.model.SubmissionStatus status, User reviewer) {
        com.wrapzzo.backend.model.Submission submission = new com.wrapzzo.backend.model.Submission(company, campaign, by, pad);
        submission.setStatus(status);
        submission.setSubmittedAt(java.time.Instant.now());
        if (reviewer != null) {
            submission.setReviewedBy(reviewer);
            submission.setReviewedAt(java.time.Instant.now());
        }
        submissionRepository.save(submission);
    }

    @Test
    void campaignSubmissionsAreCountedBucketedTrendedAndDrilledInto() {
        Company company = admin.getCompany();
        var campaign = campaignRepository.save(new com.wrapzzo.backend.model.Campaign(company, "Diwali", com.wrapzzo.backend.model.CampaignPurpose.EVENT,
                com.wrapzzo.backend.model.CampaignRecipientCategory.OTHER, java.util.Set.of(pad), "x", null, admin));
        submission(company, campaign, mrNorth1, com.wrapzzo.backend.model.SubmissionStatus.SUBMITTED, null);
        submission(company, campaign, mrNorth1, com.wrapzzo.backend.model.SubmissionStatus.RESUBMITTED, null);
        submission(company, campaign, mrNorth1, com.wrapzzo.backend.model.SubmissionStatus.APPROVED, admin);
        submission(company, campaign, mrSouth, com.wrapzzo.backend.model.SubmissionStatus.REJECTED, admin);

        actAs(admin);
        DashboardResponse all = dashboardService.summary();
        assertThat(all.submissionsPending()).isEqualTo(2);
        assertThat(all.submissionStatusBreakdown()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("PENDING", 2L, "APPROVED", 1L, "REJECTED", 1L));
        assertThat(all.activityTrend().get(11).campaignSubmissions()).isEqualTo(4);
        assertThat(all.recentActivity()).extracting(a -> a.verb()).contains("submitted a recipient for", "resubmitted a recipient for", "approved a submission for", "rejected a submission for");
        assertThat(dashboardService.drilldown("submissionStatus", "PENDING").rows()).hasSize(2);
        assertThat(dashboardService.drilldown("submissionStatus", "REJECTED").rows()).hasSize(1);
        assertThat(dashboardService.drilldown("week", LocalDate.now().with(DayOfWeek.MONDAY).toString()).rows()).hasSize(4);

        // The South MR's rejected one is outside a North MR's reach.
        actAs(mrNorth1);
        DashboardResponse north = dashboardService.summary();
        assertThat(north.submissionStatusBreakdown()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("PENDING", 2L, "APPROVED", 1L));
    }
}
