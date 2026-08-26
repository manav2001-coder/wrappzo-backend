package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.OrgUnitResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
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
 * UserService.bulkImport/OrgUnitService.listByCompany now go through CompanyAccessGuard (see
 * its own doc comment), which reads the caller from SecurityContextHolder -- same pattern
 * GiftServiceIntegrationTest/RoleServiceIntegrationTest use, with a platform-staff caller
 * (company null) since these tests exercise the service directly, not the guard itself.
 */
@SpringBootTest
@Import(TestEmailSenderConfig.class)
@Transactional
class OrgUnitServiceIntegrationTest {

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private UserService userService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    private Company company;

    @BeforeEach
    void seedCompany() {
        company = companyRepository.save(new Company("Org Unit Test Co"));

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
    void listByCompanyReflectsTheTreeBuiltDuringImportWithCorrectParentLinks() {
        userService.bulkImport(
                company.getId(),
                List.of(
                        new BulkImportUserRow("Zonal Head", "zonal@orgunit-test.com", null, "ZBM", null, null, List.of("HOSPIAID", "EAST")),
                        new BulkImportUserRow("Other Zone", "other@orgunit-test.com", null, "ZBM", null, null, List.of("HOSPIAID", "WEST"))));

        List<OrgUnitResponse> units = orgUnitService.listByCompany(company.getId());

        assertThat(units).hasSize(3); // one HOSPIAID node, shared by both EAST and WEST beneath it
        Optional<OrgUnitResponse> division = units.stream().filter(u -> u.name().equals("HOSPIAID")).findFirst();
        assertThat(division).isPresent();
        assertThat(division.get().parentId()).isNull();
        assertThat(division.get().levelName()).isEqualTo("Level 1");

        List<OrgUnitResponse> zones = units.stream().filter(u -> u.name().equals("EAST") || u.name().equals("WEST")).toList();
        assertThat(zones).hasSize(2);
        assertThat(zones).allMatch(z -> z.parentId().equals(division.get().id()));
        assertThat(zones).allMatch(z -> z.levelName().equals("Level 2"));
    }

    @Test
    void listByCompanyIsEmptyWhenNoImportHasEverUsedOrgLevels() {
        userService.bulkImport(company.getId(), List.of(new BulkImportUserRow("No Org", "noorg@orgunit-test.com", null, "KAM", null, null, null)));

        assertThat(orgUnitService.listByCompany(company.getId())).isEmpty();
    }
}
