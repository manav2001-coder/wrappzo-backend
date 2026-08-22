package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.OrgUnitResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.repository.CompanyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

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

    private Company company;

    @BeforeEach
    void seedCompany() {
        company = companyRepository.save(new Company("Org Unit Test Co"));
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
