package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.exception.CompanyVendorNotFoundException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** A vendor serves many companies, and within a company many org units (or the whole company). */
@SpringBootTest
@Transactional
class CompanyVendorServiceIntegrationTest {

    @Autowired private CompanyVendorService companyVendorService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private Vendor vendor() {
        Role role = roleRepository.save(new Role(null, "Test Platform Staff " + UUID.randomUUID()));
        User creator = userRepository.save(new User(null, role, "Staff", "staff-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        return vendorRepository.save(new Vendor("Vendor " + UUID.randomUUID(), "v-" + UUID.randomUUID() + "@x.com", null, "n/a", creator));
    }

    private Company company(String name) {
        return companyRepository.save(new Company(name + " " + UUID.randomUUID()));
    }

    private OrgUnit unit(Company company, String name) {
        return orgUnitRepository.save(new OrgUnit(company, null, name, "Zone"));
    }

    @Test
    void oneVendorServesManyCompaniesAndManyOrgUnitsWithinACompany() {
        Vendor vendor = vendor();
        Company alpha = company("Alpha");
        Company beta = company("Beta");
        OrgUnit north = unit(alpha, "North");
        OrgUnit south = unit(alpha, "South");

        companyVendorService.assign(alpha.getId(), vendor.getId(), north.getId());
        companyVendorService.assign(alpha.getId(), vendor.getId(), south.getId());
        companyVendorService.assign(beta.getId(), vendor.getId(), null);

        assertThat(companyVendorService.list(alpha.getId())).extracting(CompanyVendorResponse::orgUnitName).containsExactlyInAnyOrder("North", "South");
        assertThat(companyVendorService.list(beta.getId())).extracting(CompanyVendorResponse::orgUnitId).containsOnlyNulls();
    }

    @Test
    void assigningTheSameScopeTwiceIsIdempotent() {
        Vendor vendor = vendor();
        Company alpha = company("Alpha");

        companyVendorService.assign(alpha.getId(), vendor.getId(), null);
        List<CompanyVendorResponse> again = companyVendorService.assign(alpha.getId(), vendor.getId(), null);

        assertThat(again).hasSize(1);
    }

    @Test
    void anOrgUnitOfAnotherCompanyIsRefused() {
        Vendor vendor = vendor();
        Company alpha = company("Alpha");
        OrgUnit betaUnit = unit(company("Beta"), "Elsewhere");

        assertThatThrownBy(() -> companyVendorService.assign(alpha.getId(), vendor.getId(), betaUnit.getId()))
                .isInstanceOf(OrgUnitNotFoundException.class);
    }

    @Test
    void removingOneMappingLeavesTheVendorsOtherMappings() {
        Vendor vendor = vendor();
        Company alpha = company("Alpha");
        OrgUnit north = unit(alpha, "North");
        companyVendorService.assign(alpha.getId(), vendor.getId(), north.getId());
        List<CompanyVendorResponse> both = companyVendorService.assign(alpha.getId(), vendor.getId(), null);
        UUID northMapping = both.stream().filter(m -> m.orgUnitId() != null).findFirst().orElseThrow().mappingId();

        List<CompanyVendorResponse> left = companyVendorService.unassign(alpha.getId(), northMapping);

        assertThat(left).hasSize(1);
        assertThat(left.get(0).orgUnitId()).isNull();
    }

    @Test
    void aMappingOfAnotherCompanyCannotBeRemoved() {
        Vendor vendor = vendor();
        Company alpha = company("Alpha");
        Company beta = company("Beta");
        UUID alphaMapping = companyVendorService.assign(alpha.getId(), vendor.getId(), null).get(0).mappingId();

        assertThatThrownBy(() -> companyVendorService.unassign(beta.getId(), alphaMapping)).isInstanceOf(CompanyVendorNotFoundException.class);
        assertThat(companyVendorService.list(alpha.getId())).hasSize(1);
    }
}
