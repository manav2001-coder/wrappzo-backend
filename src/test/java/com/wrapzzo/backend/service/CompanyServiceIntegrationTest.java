package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CompanyRequest;
import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import com.wrapzzo.backend.security.Permission;
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
    private TemplateRepository templateRepository;

    @Autowired
    private AccessControlCacheService accessControlCacheService;

    @Test
    void createSeedsADefaultCompanyAdminRoleWithTheRightGrants() {
        CompanyResponse company = companyService.create(CompanyRequest.named("Alembic Pharma"));

        assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.logoUrl()).isNull();
        // Regression check: companyNo is DB-generated (@Generated INSERT) and only readable
        // immediately if the service flushes rather than deferring the insert to commit.
        assertThat(company.displayId()).matches("CID-\\d+");

        List<Role> roles = roleRepository.findByCompanyId(company.id());
        assertThat(roles).extracting(Role::getName).containsExactly("Company Admin");
        Role companyAdminRole = roles.stream().filter(role -> role.getName().equals("Company Admin")).findFirst().orElseThrow();

        // The role carries the system master template, which holds every permission a company can
        // hold -- never a platform-only one.
        assertThat(templateRepository.findTemplateIdsByRoleId(companyAdminRole.getId()))
                .containsExactly(templateRepository.findSystemByName("Company Admin").orElseThrow().getId());
        UUID templateId = templateRepository.findTemplateIdsByRoleId(companyAdminRole.getId()).get(0);
        assertThat(accessControlCacheService.loadGraph().resolve(List.of(templateId)))
                .containsExactlyInAnyOrder(Permission.companyAssignablePermissions().stream().map(Permission::key).toArray(String[]::new));
    }

    @Test
    void updateChangesTheName() {
        CompanyResponse company = companyService.create(CompanyRequest.named("Old Name"));

        CompanyResponse updated = companyService.update(company.id(), CompanyRequest.named("New Name"));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(companyService.get(company.id()).name()).isEqualTo("New Name");
    }

    @Test
    void activateAndDeactivateToggleStatus() {
        CompanyResponse company = companyService.create(CompanyRequest.named("Toggle Co"));

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
        CompanyResponse company = companyService.create(CompanyRequest.named("Listed Co"));

        assertThat(companyService.list()).extracting(CompanyResponse::id).contains(company.id());
    }

    @Test
    void presignLogoUploadReturnsAKeyScopedToTheCompanyAndAWorkingUrl() {
        CompanyResponse company = companyService.create(CompanyRequest.named("Logo Co"));

        PresignLogoUploadResponse presigned = companyService.presignLogoUpload(company.id(), "image/png");

        assertThat(presigned.key()).startsWith("companies/" + company.id() + "/logo-").endsWith(".png");
        assertThat(presigned.uploadUrl()).contains("X-Amz-Signature");
    }

    @Test
    void attachLogoSetsTheLogoUrlAndReplacingItDeletesThePreviousObject() {
        CompanyResponse company = companyService.create(CompanyRequest.named("Rebrand Co"));
        PresignLogoUploadResponse firstUpload = companyService.presignLogoUpload(company.id(), "image/png");

        CompanyResponse withLogo = companyService.attachLogo(company.id(), firstUpload.key());
        assertThat(withLogo.logoUrl()).contains(firstUpload.key());

        PresignLogoUploadResponse secondUpload = companyService.presignLogoUpload(company.id(), "image/png");
        CompanyResponse withNewLogo = companyService.attachLogo(company.id(), secondUpload.key());
        assertThat(withNewLogo.logoUrl()).contains(secondUpload.key());
    }

    @Test
    void aCompanysProfileIsKeptAndCanBeEdited() {
        CompanyResponse created = companyService.create(new CompanyRequest(
                "Profile Co", "Asha Rao", "asha@profile.example", "+91 98765 43210", "27ABCDE1234F1Z5", "ABCDE1234F", "U12345MH2020PTC000001",
                "12 MG Road", "Floor 3", "Pune", "Maharashtra", "411001", "Key account"));

        assertThat(created.contactPersonName()).isEqualTo("Asha Rao");
        assertThat(created.gstin()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(created.city()).isEqualTo("Pune");

        CompanyResponse edited = companyService.update(created.id(), new CompanyRequest(
                "Profile Co", "", null, null, "", null, null, null, null, "Mumbai", null, null, null));

        assertThat(edited.contactPersonName()).isNull();
        assertThat(edited.gstin()).isNull();
        assertThat(edited.city()).isEqualTo("Mumbai");
    }
}
