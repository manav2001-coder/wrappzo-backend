package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
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

/**
 * UserService's write methods now go through CompanyAccessGuard (see its own doc comment),
 * which reads the caller from SecurityContextHolder -- same pattern GiftServiceIntegrationTest
 * /RoleServiceIntegrationTest use, with a platform-staff caller (company null) since these
 * tests exercise the service directly, not the guard itself.
 */
@SpringBootTest
@Import(TestEmailSenderConfig.class)
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserInviteTokenRepository userInviteTokenRepository;

    @Autowired
    private TestEmailSenderConfig.RecordingEmailSender emailSender;

    private Company company;

    @BeforeEach
    void seedCompany() {
        company = companyRepository.save(new Company("Nanz Test Pharma"));
        roleRepository.save(new Role(company, "Member"));
        emailSender.sent.clear();

        Role platformRole = roleRepository.save(new Role(null, "Test Platform Staff " + UUID.randomUUID()));
        User platformStaff = userRepository.save(new User(
                null, platformRole, "Platform Staff", "platform-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(platformStaff.getId(), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importsRowsAndWiresReportsToRegardlessOfSheetOrder() {
        // The manager's row appears AFTER their report's row -- exercises the two-pass resolution.
        List<BulkImportUserRow> rows = List.of(
                new BulkImportUserRow(
                        "Arpita Banerjee", "arpita@nanz-test.com", "700378", "KAM", "Member", "3168",
                        "rajesh@nanz-test.com", List.of("HOSPIAID", "EAST")),
                new BulkImportUserRow(
                        "Rajesh Nayak", "rajesh@nanz-test.com", "943833", "RBM", "Member", "3032", null, List.of("HOSPIAID", "EAST")));

        BulkImportUsersResponse response = userService.bulkImport(company.getId(), rows);

        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.updatedCount()).isZero();

        User arpita = userRepository.findByEmail("arpita@nanz-test.com").orElseThrow();
        User rajesh = userRepository.findByEmail("rajesh@nanz-test.com").orElseThrow();
        assertThat(arpita.getReportsTo().getId()).isEqualTo(rajesh.getId());
        assertThat(arpita.getStatus()).isEqualTo(UserStatus.INVITED);
        // The invite tokens are created synchronously (that part has to be, so a setup link
        // is valid immediately) -- the emails themselves are dispatched asynchronously by
        // UserInviteEmailListener after this transaction commits, which never happens in a
        // @Transactional test (it rolls back), so emailSender.sent can't be asserted here.
        // See UserInviteEmailListenerTest for coverage of the actual send logic.
        assertThat(userInviteTokenRepository.findAll())
                .extracting(token -> token.getUser().getEmail())
                .contains("arpita@nanz-test.com", "rajesh@nanz-test.com");
    }

    @Test
    void sharedOrgUnitBranchIsReusedNotDuplicated() {
        List<BulkImportUserRow> rows = List.of(
                new BulkImportUserRow("Arpita Banerjee", "arpita2@nanz-test.com", null, "KAM", "Member", null, null, List.of("HOSPIAID", "EAST")),
                new BulkImportUserRow("Another KAM", "another@nanz-test.com", null, "KAM", "Member", null, null, List.of("HOSPIAID", "EAST")));

        userService.bulkImport(company.getId(), rows);

        List<OrgUnit> units = orgUnitRepository.findByCompanyId(company.getId());
        assertThat(units).hasSize(2); // one "HOSPIAID" node, one "EAST" node under it -- not four
        assertThat(units).extracting(OrgUnit::getName).containsExactlyInAnyOrder("HOSPIAID", "EAST");

        User a = userRepository.findByEmail("arpita2@nanz-test.com").orElseThrow();
        User b = userRepository.findByEmail("another@nanz-test.com").orElseThrow();
        assertThat(a.getOrgUnit().getId()).isEqualTo(b.getOrgUnit().getId());
    }

    @Test
    void reimportingTheSameEmailUpdatesInsteadOfDuplicating() {
        userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("Old Name", "reimport@nanz-test.com", null, "KAM", "Member", null, null, null)));

        BulkImportUsersResponse second = userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("New Name", "reimport@nanz-test.com", "999", "SR.KAM", "Member", "EMP1", null, null)));

        assertThat(second.createdCount()).isZero();
        assertThat(second.updatedCount()).isEqualTo(1);
        assertThat(userRepository.findByEmail("reimport@nanz-test.com").orElseThrow().getName()).isEqualTo("New Name");
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("reimport@nanz-test.com")).count()).isEqualTo(1);
    }

    @Test
    void unresolvableReportsToEmailFailsValidationBeforeAnyWrite() {
        List<BulkImportUserRow> rows = List.of(
                new BulkImportUserRow("Ghost Report", "ghost@nanz-test.com", null, "KAM", "Member", null, "nobody@nanz-test.com", null));

        assertThatThrownBy(() -> userService.bulkImport(company.getId(), rows)).isInstanceOf(UserImportValidationException.class);
        assertThat(userRepository.findByEmail("ghost@nanz-test.com")).isEmpty();
    }

    @Test
    void duplicateEmailWithinTheSameSheetFailsValidation() {
        List<BulkImportUserRow> rows = List.of(
                new BulkImportUserRow("First", "dup2@nanz-test.com", null, "KAM", "Member", null, null, null),
                new BulkImportUserRow("Second", "dup2@nanz-test.com", null, "KAM", "Member", null, null, null));

        assertThatThrownBy(() -> userService.bulkImport(company.getId(), rows)).isInstanceOf(UserImportValidationException.class);
    }

    @Test
    void duplicateEmployeeCodeWithinTheSameSheetFailsValidationWithASpecificMessage() {
        List<BulkImportUserRow> rows = List.of(
                new BulkImportUserRow("First", "code1@nanz-test.com", null, "KAM", "Member", "DUP-1", null, null),
                new BulkImportUserRow("Second", "code2@nanz-test.com", null, "KAM", "Member", "DUP-1", null, null));

        assertThatThrownBy(() -> userService.bulkImport(company.getId(), rows))
                .isInstanceOf(UserImportValidationException.class)
                .satisfies(e -> assertThat(((UserImportValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("DUP-1") && msg.contains("code1@nanz-test.com")));
        assertThat(userRepository.findByEmail("code1@nanz-test.com")).isEmpty();
        assertThat(userRepository.findByEmail("code2@nanz-test.com")).isEmpty();
    }

    @Test
    void employeeCodeAlreadyUsedByAnExistingUserFailsValidation() {
        userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("Existing", "existing-code@nanz-test.com", null, "KAM", "Member", "EXIST-1", null, null)));

        List<BulkImportUserRow> rows = List.of(new BulkImportUserRow("New Person", "new-code@nanz-test.com", null, "KAM", "Member", "EXIST-1", null, null));

        assertThatThrownBy(() -> userService.bulkImport(company.getId(), rows)).isInstanceOf(UserImportValidationException.class);
        assertThat(userRepository.findByEmail("new-code@nanz-test.com")).isEmpty();
    }

    @Test
    void emailAlreadyBelongingToAnotherCompanyFailsValidation() {
        Company otherCompany = companyRepository.save(new Company("Other Test Co"));
        roleRepository.save(new Role(otherCompany, "Member"));
        userService.bulkImport(
                otherCompany.getId(),
                List.of(new BulkImportUserRow("Existing Elsewhere", "elsewhere@nanz-test.com", null, "KAM", "Member", null, null, null)));

        assertThatThrownBy(() -> userService.bulkImport(
                        company.getId(),
                        List.of(new BulkImportUserRow("Same Email", "elsewhere@nanz-test.com", null, "KAM", "Member", null, null, null))))
                .isInstanceOf(UserImportValidationException.class);
    }

    @Test
    void listByCompanyReturnsImportedUsers() {
        userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("Listed Person", "listed@nanz-test.com", null, "KAM", "Member", null, null, null)));

        List<UserResponse> listed = userService.listByCompany(company.getId());
        assertThat(listed).extracting(UserResponse::email).contains("listed@nanz-test.com");
    }

    @Test
    void reportsToCanReferenceAUserFromAPreviousImportRun() {
        userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("Manager", "manager@nanz-test.com", null, "ZBM", "Member", null, null, null)));

        userService.bulkImport(
                company.getId(),
                List.of(new BulkImportUserRow("New Report", "report@nanz-test.com", null, "KAM", "Member", null, "manager@nanz-test.com", null)));

        User report = userRepository.findByEmail("report@nanz-test.com").orElseThrow();
        User manager = userRepository.findByEmail("manager@nanz-test.com").orElseThrow();
        assertThat(report.getReportsTo().getId()).isEqualTo(manager.getId());
    }
}
