package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CompanyServiceIntegrationTest {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleAccessControlRepository roleAccessControlRepository;

    @Test
    void createSeedsADefaultCompanyAdminRoleWithTheRightGrants() {
        CompanyResponse company = companyService.create("Alembic Pharma");

        assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.logoUrl()).isNull();

        List<Role> roles = roleRepository.findByCompanyId(company.id());
        assertThat(roles).hasSize(1);
        Role companyAdminRole = roles.get(0);
        assertThat(companyAdminRole.getName()).isEqualTo("Company Admin");

        List<String> grantedKeys = roleAccessControlRepository.findAccessControlKeysByRoleId(companyAdminRole.getId());
        assertThat(grantedKeys).containsExactlyInAnyOrder("user.manage", "role.manage");
    }

    @Test
    void updateChangesTheName() {
        CompanyResponse company = companyService.create("Old Name");

        CompanyResponse updated = companyService.update(company.id(), "New Name");

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(companyService.get(company.id()).name()).isEqualTo("New Name");
    }

    @Test
    void activateAndDeactivateToggleStatus() {
        CompanyResponse company = companyService.create("Toggle Co");

        companyService.setStatus(company.id(), CompanyStatus.INACTIVE);
        assertThat(companyService.get(company.id()).status()).isEqualTo(CompanyStatus.INACTIVE);

        companyService.setStatus(company.id(), CompanyStatus.ACTIVE);
        assertThat(companyService.get(company.id()).status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void getUnknownCompanyThrows() {
        assertThatThrownBy(() -> companyService.get(UUID.randomUUID())).isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void listIncludesCreatedCompanies() {
        CompanyResponse company = companyService.create("Listed Co");

        assertThat(companyService.list()).extracting(CompanyResponse::id).contains(company.id());
    }

    @Test
    void presignLogoUploadReturnsAKeyScopedToTheCompanyAndAWorkingUrl() {
        CompanyResponse company = companyService.create("Logo Co");

        PresignLogoUploadResponse presigned = companyService.presignLogoUpload(company.id(), "image/png");

        assertThat(presigned.key()).startsWith("companies/" + company.id() + "/logo-").endsWith(".png");
        assertThat(presigned.uploadUrl()).contains("X-Amz-Signature");
    }

    @Test
    void attachLogoSetsTheLogoUrlAndReplacingItDeletesThePreviousObject() {
        CompanyResponse company = companyService.create("Rebrand Co");
        PresignLogoUploadResponse firstUpload = companyService.presignLogoUpload(company.id(), "image/png");

        CompanyResponse withLogo = companyService.attachLogo(company.id(), firstUpload.key());
        assertThat(withLogo.logoUrl()).contains(firstUpload.key());

        PresignLogoUploadResponse secondUpload = companyService.presignLogoUpload(company.id(), "image/png");
        CompanyResponse withNewLogo = companyService.attachLogo(company.id(), secondUpload.key());
        assertThat(withNewLogo.logoUrl()).contains(secondUpload.key());
    }
}
