package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.dto.EditCompanyVendorRequest;
import com.wrapzzo.backend.dto.SetVendorCoverageRequest;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidVendorCoverageException;
import com.wrapzzo.backend.exception.NoAvailableVendorException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** The company admin maps vendors to divisions and units; an order then only goes to a vendor that serves where it was raised. */
@SpringBootTest
@Import(TestEmailSenderConfig.class)
@Transactional
class CompanyVendorMappingIntegrationTest {

    @Autowired private TestEmailSenderConfig.RecordingEmailSender emailSender;

    @Autowired private CompanyVendorService companyVendorService;
    @Autowired private OrderService orderService;
    @Autowired private VendorDivisionService vendorDivisionService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private OrderRepository orderRepository;

    private Company company;
    private User staff;
    private OrgUnit summit;
    private OrgUnit summitLucknow;
    private OrgUnit megacare;
    private Gift gift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Mapping Co " + UUID.randomUUID()));
        Role role = roleRepository.save(new Role(company, "Mapper " + UUID.randomUUID()));
        staff = userRepository.save(new User(company, role, "Staff", "map-" + UUID.randomUUID() + "@x.com", "n/a"));
        summit = orgUnitRepository.save(new OrgUnit(company, null, "Summit", "Division"));
        summitLucknow = orgUnitRepository.save(new OrgUnit(company, summit, "Lucknow", "Zone"));
        megacare = orgUnitRepository.save(new OrgUnit(company, null, "Megacare", "Division"));
        emailSender.sent.clear();
        gift = giftRepository.save(new Gift("Pad", "Stationery", "A pad", null, null, staff));
        // Assigning a vendor records who moved the order, so there has to be a signed-in user.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(staff.getId(), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Vendor vendor(String name) {
        Vendor vendor = new Vendor(name + " " + UUID.randomUUID(), "v-" + UUID.randomUUID() + "@x.com", null, "n/a", staff);
        vendor.setCompany(company);
        return vendorRepository.save(vendor);
    }

    private Order orderRaisedIn(OrgUnit unit) {
        User am = userRepository.save(new User(company, staff.getRole(), "AM", "am-" + UUID.randomUUID() + "@x.com", "n/a"));
        am.setOrgUnit(unit);
        return orderRepository.save(new Order(company, gift, OrderSource.QUICK_GIFT, "Dr A", "9999999999", am));
    }

    private SetVendorCoverageRequest only(OrgUnit... units) {
        return new SetVendorCoverageRequest(false, java.util.Arrays.stream(units).map(OrgUnit::getId).toList());
    }

    @Test
    void aVendorSeesTheDivisionsItIsMappedTo_aZoneCountsAsItsDivision_aWholeCompanyMappingIsEveryDivision() {
        Vendor vendor = vendor("Printer");
        companyVendorService.setCoverage(company.getId(), vendor.getId(), only(summitLucknow, megacare));
        assertThat(vendorDivisionService.servedDivisions(vendor.getId()))
                .extracting(com.wrapzzo.backend.dto.VendorDivisionResponse::name).containsExactly("Megacare", "Summit");

        companyVendorService.setCoverage(company.getId(), vendor.getId(), new SetVendorCoverageRequest(true, List.of()));
        assertThat(vendorDivisionService.servedDivisions(vendor.getId()))
                .extracting(com.wrapzzo.backend.dto.VendorDivisionResponse::name).containsExactly("Megacare", "Summit");
    }

    @Test
    void settingCoverageReplacesWhatTheVendorServedInThisCompany() {
        Vendor vendor = vendor("Printer");
        companyVendorService.setCoverage(company.getId(), vendor.getId(), only(summit, megacare));

        List<CompanyVendorResponse> after = companyVendorService.setCoverage(company.getId(), vendor.getId(), only(megacare));

        assertThat(after).extracting(CompanyVendorResponse::orgUnitName).containsExactly("Megacare");
    }

    @Test
    void wholeCompanyReplacesUnitMappingsAndBackAgain() {
        Vendor vendor = vendor("Printer");
        companyVendorService.setCoverage(company.getId(), vendor.getId(), only(summit));

        List<CompanyVendorResponse> whole = companyVendorService.setCoverage(company.getId(), vendor.getId(), new SetVendorCoverageRequest(true, List.of()));
        assertThat(whole).extracting(CompanyVendorResponse::orgUnitId).containsOnlyNulls().hasSize(1);

        List<CompanyVendorResponse> back = companyVendorService.setCoverage(company.getId(), vendor.getId(), only(megacare));
        assertThat(back).extracting(CompanyVendorResponse::orgUnitName).containsExactly("Megacare");
    }

    @Test
    void anEmptyChoiceOrBothChoicesAreRefusedWithAReason() {
        Vendor vendor = vendor("Printer");
        assertThatThrownBy(() -> companyVendorService.setCoverage(company.getId(), vendor.getId(), new SetVendorCoverageRequest(false, List.of())))
                .isInstanceOf(InvalidVendorCoverageException.class)
                .hasMessageContaining("at least one division");
        assertThatThrownBy(() -> companyVendorService.setCoverage(company.getId(), vendor.getId(), new SetVendorCoverageRequest(true, List.of(summit.getId()))))
                .isInstanceOf(InvalidVendorCoverageException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void aUnitOfAnotherCompanyIsRefused() {
        Vendor vendor = vendor("Printer");
        OrgUnit foreign = orgUnitRepository.save(new OrgUnit(companyRepository.save(new Company("Other " + UUID.randomUUID())), null, "Elsewhere", "Division"));
        assertThatThrownBy(() -> companyVendorService.setCoverage(company.getId(), vendor.getId(), only(foreign)))
                .isInstanceOf(OrgUnitNotFoundException.class);
    }

    @Test
    void aCompanyAddsItsOwnVendorMappedToDivisionsAndItIsInvitedAndOwned() {
        List<CompanyVendorResponse> roster = companyVendorService.addVendor(company.getId(),
                new AddCompanyVendorRequest("Own Printer", "own-" + UUID.randomUUID() + "@x.com", "999", false, List.of(summit.getId(), megacare.getId())));

        assertThat(roster).extracting(CompanyVendorResponse::vendorName).containsOnly("Own Printer");
        assertThat(roster).extracting(CompanyVendorResponse::orgUnitName).containsExactlyInAnyOrder("Summit", "Megacare");
        UUID vendorId = roster.get(0).vendorId();
        assertThat(vendorRepository.findById(vendorId).orElseThrow().getCompany().getId()).isEqualTo(company.getId());
        assertThat(emailSender.sent).hasSize(1);
    }

    @Test
    void aRefusedMappingLeavesNoVendorAndNoInviteBehind() {
        long before = vendorRepository.count();

        assertThatThrownBy(() -> companyVendorService.addVendor(company.getId(),
                new AddCompanyVendorRequest("Nowhere", "nowhere-" + UUID.randomUUID() + "@x.com", null, false, List.of())))
                .isInstanceOf(InvalidVendorCoverageException.class);

        assertThat(vendorRepository.count()).isEqualTo(before);
        assertThat(emailSender.sent).isEmpty();
    }

    @Test
    void aCompanyCannotTouchAnotherCompanysVendor() {
        Company other = companyRepository.save(new Company("Other " + UUID.randomUUID()));
        Vendor theirs = vendor("Theirs");
        companyVendorService.setCoverage(company.getId(), theirs.getId(), only(summit));

        assertThatThrownBy(() -> companyVendorService.setCoverage(other.getId(), theirs.getId(), new SetVendorCoverageRequest(true, List.of())))
                .isInstanceOf(VendorNotFoundException.class);
        assertThatThrownBy(() -> companyVendorService.editVendor(other.getId(), theirs.getId(), new EditCompanyVendorRequest("Mine now", null)))
                .isInstanceOf(VendorNotFoundException.class);
        assertThatThrownBy(() -> companyVendorService.setVendorStatus(other.getId(), theirs.getId(), VendorStatus.SUSPENDED))
                .isInstanceOf(VendorNotFoundException.class);
        assertThatThrownBy(() -> companyVendorService.resendInvite(other.getId(), theirs.getId()))
                .isInstanceOf(VendorNotFoundException.class);
    }

    @Test
    void aCompanyEditsAndSuspendsItsOwnVendorAndASuspendedOneIsNotMapped() {
        Vendor vendor = vendor("Printer");
        companyVendorService.setCoverage(company.getId(), vendor.getId(), only(summit));

        companyVendorService.editVendor(company.getId(), vendor.getId(), new EditCompanyVendorRequest("Renamed", "555"));
        List<CompanyVendorResponse> roster = companyVendorService.setVendorStatus(company.getId(), vendor.getId(), VendorStatus.SUSPENDED);

        assertThat(roster).extracting(CompanyVendorResponse::vendorName).containsOnly("Renamed");
        assertThat(roster).extracting(CompanyVendorResponse::vendorStatus).containsOnly(VendorStatus.SUSPENDED);
        assertThatThrownBy(() -> companyVendorService.setCoverage(company.getId(), vendor.getId(), only(megacare)))
                .isInstanceOf(InvalidVendorCoverageException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void aSuspendedVendorIsNotUsedToRouteAnOrder() {
        Vendor vendor = vendor("Printer");
        companyVendorService.setCoverage(company.getId(), vendor.getId(), only(summit));
        companyVendorService.setVendorStatus(company.getId(), vendor.getId(), VendorStatus.SUSPENDED);
        Order order = orderRaisedIn(summitLucknow);

        assertThatThrownBy(() -> orderService.autoAssignVendor(order.getId())).isInstanceOf(NoAvailableVendorException.class);
    }

    @Test
    void anOrderGoesToAVendorMappedToItsDivisionOrUnitNotAnotherDivisions() {
        Vendor summitPrinter = vendor("Summit printer");
        Vendor megacarePrinter = vendor("Megacare printer");
        companyVendorService.setCoverage(company.getId(), summitPrinter.getId(), only(summit));
        companyVendorService.setCoverage(company.getId(), megacarePrinter.getId(), only(megacare));

        Order order = orderRaisedIn(summitLucknow);
        orderService.autoAssignVendor(order.getId());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getAssignedVendor().getId()).isEqualTo(summitPrinter.getId());
    }

    @Test
    void anOrderWithNoVendorForItsDivisionSaysSoAndNamesTheUnit() {
        companyVendorService.setCoverage(company.getId(), vendor("Megacare printer").getId(), only(megacare));
        Order order = orderRaisedIn(summitLucknow);

        assertThatThrownBy(() -> orderService.autoAssignVendor(order.getId()))
                .isInstanceOf(NoAvailableVendorException.class)
                .hasMessageContaining("Lucknow")
                .hasMessageContaining("map a vendor");
    }

    @Test
    void aWholeCompanyVendorServesEveryDivision() {
        Vendor everywhere = vendor("Everywhere");
        companyVendorService.setCoverage(company.getId(), everywhere.getId(), new SetVendorCoverageRequest(true, List.of()));

        Order order = orderRaisedIn(megacare);
        orderService.autoAssignVendor(order.getId());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getAssignedVendor().getId()).isEqualTo(everywhere.getId());
    }

    @Test
    void handPickingAVendorOfAnotherDivisionIsRefusedWithAReason() {
        Vendor megacarePrinter = vendor("Megacare printer");
        companyVendorService.setCoverage(company.getId(), megacarePrinter.getId(), only(megacare));
        Order order = orderRaisedIn(summitLucknow);

        assertThatThrownBy(() -> orderService.assignVendorManually(order.getId(), megacarePrinter.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("isn't mapped to Lucknow");
    }
}
