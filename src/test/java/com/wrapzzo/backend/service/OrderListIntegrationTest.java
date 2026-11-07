package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.list.InvalidListQueryException;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
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
 * The Orders list is paged, searched, filtered and sorted in the database, inside what the caller may see:
 * a company-wide user pages through everyone's; a team member only their area's; vendor names stay out of reach of anyone but the admin.
 */
@SpringBootTest
@Transactional
class OrderListIntegrationTest {

    @Autowired private QuickGiftService quickGiftService;
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

    private User mrNorth;
    private User mrSouth;
    private User admin;
    private Company company;
    private Vendor printer;
    private Gift pad;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("List Test Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        Role member = roleRepository.save(new Role(company, "Member"));
        Role adminRole = roleRepository.save(new Role(company, "Admin"));
        grant(adminRole, "vendor_identity.view");
        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        OrgUnit north = orgUnitRepository.save(new OrgUnit(company, division, "North", "Area"));
        OrgUnit south = orgUnitRepository.save(new OrgUnit(company, division, "South", "Area"));
        mrNorth = user("MR North", member, north);
        mrSouth = user("MR South", member, south);
        admin = user("Admin", adminRole, null);
        admin.setCompanyWide(true);
        printer = vendorRepository.save(new Vendor("Secret Printers", "p-" + UUID.randomUUID() + "@test.com", null, "n/a", mrNorth));
        companyVendorRepository.save(new CompanyVendor(company, printer, null));
        pad = new Gift("Prescription Pad " + UUID.randomUUID(), "Stationery", "A5", null, 7, printer);
        pad.setModules(EnumSet.allOf(GiftModule.class));
        pad.setPrice(BigDecimal.valueOf(40));
        pad = giftRepository.save(pad);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void grant(Role role, String key) {
        Template template = templateRepository.save(new Template(role.getCompany(), role.getName() + " access " + UUID.randomUUID(), null));
        role.getTemplates().add(template);
        roleRepository.save(role);
        template.getAccessControls().add(accessControlRepository.findByKey(key).orElseThrow());
        templateRepository.save(template);
    }

    private User user(String name, Role role, OrgUnit unit) {
        User user = new User(company, role, name, "l-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void order(User by, String doctor, int quantity) {
        actAs(by);
        quickGiftService.create(new CreateQuickGiftRequest(pad.getId(), null, doctor, "98000" + doctor.hashCode(), "PC-" + doctor.replace(" ", ""), null, "Road 1", null, null, null,
                null, new DoctorDetails("MBBS", "Cardiology", "Clinic"), quantity));
    }

    private PageResponse<OrderResponse> page(User as, Integer page, Integer size, String search, String sort, String dir, String filters) {
        actAs(as);
        return orderService.page(new ListQueryParams(page, size, search, sort, dir, filters).toQuery());
    }

    private void seed() {
        for (int i = 1; i <= 12; i++) {
            order(mrNorth, "Dr North " + i, i);
        }
        for (int i = 1; i <= 5; i++) {
            order(mrSouth, "Dr South " + i, 100 + i);
        }
    }

    @Test
    void aCompanyWideUserPagesThroughEveryOrderWithTheTotalAndNoMoreThanAPage() {
        seed();

        PageResponse<OrderResponse> first = page(admin, 0, 10, null, null, null, null);
        assertThat(first.total()).isEqualTo(17);
        assertThat(first.data()).hasSize(10);
        assertThat(first.hasMore()).isTrue();

        PageResponse<OrderResponse> second = page(admin, 1, 10, null, null, null, null);
        assertThat(second.data()).hasSize(7);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.data()).extracting(OrderResponse::id).doesNotContainAnyElementsOf(first.data().stream().map(OrderResponse::id).toList());
    }

    @Test
    void aTeamMemberSeesOnlyTheirOwnAreasOrders() {
        seed();

        PageResponse<OrderResponse> north = page(mrNorth, 0, 50, null, null, null, null);
        assertThat(north.total()).isEqualTo(12);
        assertThat(north.data()).extracting(OrderResponse::recipientName).allMatch(name -> name.startsWith("Dr North"));
        assertThat(page(mrSouth, 0, 50, null, null, null, null).total()).isEqualTo(5);
    }

    @Test
    void searchFiltersAndSortWorkInTheDatabase() {
        seed();

        assertThat(page(admin, 0, 50, "dr south", null, null, null).total()).isEqualTo(5);
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"quantity\",\"operator\":\"gte\",\"value\":101}]").total()).isEqualTo(5);
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"quantity\",\"operator\":\"between\",\"value\":[3,5]}]").total()).isEqualTo(3);
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"recipientName\",\"operator\":\"contains\",\"value\":\"north 1\"}]").total()).isEqualTo(4); // 1, 10, 11, 12
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"status\",\"operator\":\"in\",\"value\":[\"PRODUCTION_QUEUE\",\"DELIVERED\"]}]").total()).isEqualTo(17);
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"DELIVERED\"}]").total()).isZero();

        PageResponse<OrderResponse> biggestFirst = page(admin, 0, 3, null, "quantity", "desc", null);
        assertThat(biggestFirst.data()).extracting(OrderResponse::quantity).containsExactly(105, 104, 103);
        assertThat(page(admin, 0, 3, null, "recipientName", "asc", null).data().get(0).recipientName()).isEqualTo("Dr North 1");
    }

    @Test
    void theOrderNumberCanBeSearchedAsItIsShown() {
        seed();
        PageResponse<OrderResponse> all = page(admin, 0, 100, null, null, null, null);
        String displayId = all.data().get(0).displayId();

        assertThat(page(admin, 0, 50, displayId, null, null, null).data()).extracting(OrderResponse::displayId).contains(displayId);
        assertThat(page(admin, 0, 50, null, null, null, "[{\"field\":\"displayId\",\"operator\":\"contains\",\"value\":\"" + displayId + "\"}]").data())
                .extracting(OrderResponse::displayId).contains(displayId);
    }

    @Test
    void theVendorIsNotSomethingToSearchFilterOrSortByUnlessTheCallerMayKnowIt() {
        seed();

        assertThat(page(admin, 0, 50, "secret printers", null, null, null).total()).isEqualTo(17);
        assertThat(page(admin, 0, 5, null, "assignedVendorName", "asc", null).data()).hasSize(5);

        // The same words find nothing for an AM, and the field is not on offer at all.
        assertThat(page(mrNorth, 0, 50, "secret printers", null, null, null).total()).isZero();
        assertThatThrownBy(() -> page(mrNorth, 0, 50, null, null, null, "[{\"field\":\"assignedVendorName\",\"operator\":\"contains\",\"value\":\"secret\"}]"))
                .isInstanceOf(InvalidListQueryException.class).hasMessageContaining("Cannot filter by 'assignedVendorName'");
        assertThatThrownBy(() -> page(mrNorth, 0, 50, null, "assignedVendorName", "asc", null)).isInstanceOf(InvalidListQueryException.class);
        assertThat(page(mrNorth, 0, 50, null, null, null, null).data()).allSatisfy(order -> assertThat(order.assignedVendorName()).isNull());
    }

    @Test
    void aRequestForSomethingThatCannotBeAnsweredSaysWhy() {
        seed();

        assertThatThrownBy(() -> page(admin, 0, 10, null, "password", "asc", null)).isInstanceOf(InvalidListQueryException.class).hasMessageContaining("Cannot sort by 'password'");
        assertThatThrownBy(() -> page(admin, 0, 10, null, null, null, "[{\"field\":\"owner.email\",\"operator\":\"contains\",\"value\":\"a\"}]"))
                .isInstanceOf(InvalidListQueryException.class).hasMessageContaining("You can filter by:");
        assertThatThrownBy(() -> page(admin, 0, 10, null, null, null, "not json")).isInstanceOf(InvalidListQueryException.class).hasMessageContaining("filters could not be read");
        assertThatThrownBy(() -> page(admin, 0, 10, null, null, null, "[{\"field\":\"quantity\",\"operator\":\"gte\",\"value\":\"abc\"}]")).isInstanceOf(InvalidListQueryException.class).hasMessageContaining("not a number");
        assertThatThrownBy(() -> page(admin, 0, 10, null, null, null, "[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"LOST\"}]")).isInstanceOf(InvalidListQueryException.class).hasMessageContaining("not a value of 'status'");
        assertThatThrownBy(() -> page(admin, -1, 10, null, null, null, null)).isInstanceOf(InvalidListQueryException.class);
    }

    @Test
    void thePageSizeIsCappedSoNoScreenCanAskForTheWholeTable() {
        seed();
        assertThat(page(admin, 0, 100000, null, null, null, null).pageSize()).isEqualTo(100);
    }

    @Test
    void summaryCountsOverTheSameOrdersAsTheList() {
        seed();
        actAs(mrNorth);
        var summary = orderService.summary();
        assertThat(summary.total()).isEqualTo(12);
        assertThat(summary.byStatus().get(OrderStatus.PRODUCTION_QUEUE)).isEqualTo(12L);
    }
}
