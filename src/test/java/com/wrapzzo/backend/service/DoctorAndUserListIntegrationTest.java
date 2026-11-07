package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.list.InvalidListQueryException;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
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

/** The Doctors and Users lists page in the database, inside what each caller may reach. */
@SpringBootTest
@Transactional
class DoctorAndUserListIntegrationTest {

    @Autowired private RecipientService recipientService;
    @Autowired private UserService userService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;

    private Company company;
    private Role member;
    private User amNorth;
    private User amSouth;
    private User zonalManager;
    private User companyHead;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Doctor List Pharma " + UUID.randomUUID()));
        member = roleRepository.save(new Role(company, "AM"));
        Role headRole = roleRepository.save(new Role(company, "Head"));
        OrgUnit division = orgUnitRepository.save(new OrgUnit(company, null, "Division", "Division"));
        OrgUnit north = orgUnitRepository.save(new OrgUnit(company, division, "North", "Zone"));
        OrgUnit south = orgUnitRepository.save(new OrgUnit(company, division, "South", "Zone"));
        amNorth = user("AM North", member, north, null);
        amSouth = user("AM South", member, south, null);
        zonalManager = user("ZM North", member, north, null);
        amNorth.setReportsTo(zonalManager);
        companyHead = user("Head", headRole, null, null);
        companyHead.setCompanyWide(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User user(String name, Role role, OrgUnit unit, User boss) {
        User user = new User(company, role, name, "d-" + UUID.randomUUID() + "@test.com", "n/a");
        user.setOrgUnit(unit);
        user.setReportsTo(boss);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void addDoctor(User by, String name, String pcode) {
        actAs(by);
        recipientService.createMine(new CreateRecipientRequest(name, "9" + Math.abs(name.hashCode()), pcode, null, "Road", null, new DoctorDetails("MBBS", "Cardiology", "Clinic")));
    }

    private PageResponse<RecipientResponse> doctors(User as, Integer page, Integer size, String search, String sort, String dir, String filters) {
        actAs(as);
        return recipientService.page(new ListQueryParams(page, size, search, sort, dir, filters).toQuery());
    }

    private void seedDoctors() {
        for (int i = 1; i <= 12; i++) {
            addDoctor(amNorth, "Dr North " + i, "N-" + i);
        }
        for (int i = 1; i <= 4; i++) {
            addDoctor(amSouth, "Dr South " + i, "S-" + i);
        }
    }

    @Test
    void anAmPagesThroughOnlyTheirOwnList() {
        seedDoctors();

        PageResponse<RecipientResponse> first = doctors(amNorth, 0, 5, null, null, null, null);
        assertThat(first.total()).isEqualTo(12);
        assertThat(first.data()).hasSize(5).allSatisfy(d -> assertThat(d.inMyList()).isTrue());
        assertThat(first.hasMore()).isTrue();
        assertThat(doctors(amSouth, 0, 50, null, null, null, null).total()).isEqualTo(4);
    }

    @Test
    void aManagerReachesTheirTeamsDoctorsAndTheCompanyHeadEveryones() {
        seedDoctors();

        PageResponse<RecipientResponse> zm = doctors(zonalManager, 0, 50, null, null, null, null);
        assertThat(zm.total()).isEqualTo(12);
        assertThat(zm.data()).allSatisfy(d -> assertThat(d.inMyList()).isFalse());
        assertThat(doctors(companyHead, 0, 100, null, null, null, null).total()).isEqualTo(16);
    }

    @Test
    void searchFilterAndSortAreDoneInTheDatabase() {
        seedDoctors();

        assertThat(doctors(companyHead, 0, 50, "s-3", null, null, null).data()).extracting(RecipientResponse::pcode).containsExactly("S-3");
        assertThat(doctors(companyHead, 0, 50, null, null, null, "[{\"field\":\"name\",\"operator\":\"contains\",\"value\":\"south\"}]").total()).isEqualTo(4);
        assertThat(doctors(companyHead, 0, 50, null, null, null, "[{\"field\":\"createdByName\",\"operator\":\"equals\",\"value\":\"AM North\"}]").total()).isEqualTo(12);
        assertThat(doctors(companyHead, 0, 3, null, "pcode", "desc", null).data()).extracting(RecipientResponse::pcode).containsExactly("S-4", "S-3", "S-2");
        assertThat(doctors(companyHead, 0, 50, "50%", null, null, null).total()).isZero(); // a % is searched for, not a wildcard
    }

    @Test
    void theCountIsOfTheMatchesNotThePageAndPagesDoNotOverlap() {
        seedDoctors();
        var one = doctors(companyHead, 0, 10, null, "name", "asc", null);
        var two = doctors(companyHead, 1, 10, null, "name", "asc", null);
        assertThat(one.total()).isEqualTo(16);
        assertThat(two.data()).hasSize(6);
        assertThat(two.data()).extracting(RecipientResponse::id).doesNotContainAnyElementsOf(one.data().stream().map(RecipientResponse::id).toList());
    }

    @Test
    void unknownFieldsAreRefusedWithTheListOfWhatIsOffered() {
        assertThatThrownBy(() -> doctors(companyHead, 0, 10, null, null, null, "[{\"field\":\"company\",\"operator\":\"contains\",\"value\":\"x\"}]"))
                .isInstanceOf(InvalidListQueryException.class).hasMessageContaining("pcode");
    }

    // --- users ---

    private PageResponse<UserResponse> users(User as, String search, String sort, String dir, String filters, Integer size) {
        actAs(as);
        return userService.pageByCompany(company.getId(), new ListQueryParams(0, size, search, sort, dir, filters).toQuery());
    }

    @Test
    void theUsersListPagesTheirPeopleAndNeverTheCaller() {
        PageResponse<UserResponse> all = users(companyHead, null, "name", "asc", null, 50);
        assertThat(all.data()).extracting(UserResponse::name).containsExactly("AM North", "AM South", "ZM North");
        assertThat(all.total()).isEqualTo(3);

        // The ZM looks after their area: AM North (and not themselves, nor the other zone).
        assertThat(users(zonalManager, null, null, null, null, 50).data()).extracting(UserResponse::name).containsExactly("AM North");
    }

    @Test
    void usersCanBeSearchedByRoleAndEmailAndFilteredByStatus() {
        // The ZM themself, and the AM who reports to them (the search reads the reports-to name too).
        assertThat(users(companyHead, "zm north", null, null, null, 50).data()).extracting(UserResponse::name).containsExactlyInAnyOrder("ZM North", "AM North");
        assertThat(users(companyHead, null, null, null, "[{\"field\":\"roleName\",\"operator\":\"equals\",\"value\":\"AM\"}]", 50).total()).isEqualTo(3);
        assertThat(users(companyHead, null, null, null, "[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"INVITED\"}]", 50).total()).isZero();
        assertThat(users(companyHead, null, null, null, "[{\"field\":\"status\",\"operator\":\"in\",\"value\":[\"ACTIVE\",\"INVITED\"]}]", 50).total()).isEqualTo(3);
        assertThat(users(companyHead, null, "reportsToName", "asc", null, 2).data()).hasSize(2);
    }
}
