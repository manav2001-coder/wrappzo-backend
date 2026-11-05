package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** The Company Admin always holds every company key -- but a template another role shares is never topped up. */
@SpringBootTest
@Transactional
class CompanyAdminAccessReconcilerIntegrationTest {

    @Autowired private CompanyAdminAccessReconciler reconciler;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private AccessControlRepository accessControlRepository;

    @Test
    void aCompanyAdminTemplateGetsEveryCompanyKey_aSharedOneDoesNot() {
        Company company = companyRepository.save(new Company("Reconcile Pharma " + UUID.randomUUID()));
        AccessControl one = accessControlRepository.findByKey("dashboard.view").orElseThrow();

        Template adminOnly = new Template(company, "Admin access " + UUID.randomUUID(), null);
        adminOnly.getAccessControls().add(one);
        adminOnly = templateRepository.save(adminOnly);
        Role admin = new Role(company, "Company Admin");
        admin.getTemplates().add(adminOnly);
        roleRepository.save(admin);

        Template shared = new Template(company, "Shared access " + UUID.randomUUID(), null);
        shared.getAccessControls().add(one);
        shared = templateRepository.save(shared);
        Role sharedAdmin = new Role(company, "Company Admin");
        // A second role of another name uses the same template, so it must be left alone.
        Role member = new Role(company, "Member");
        member.getTemplates().add(shared);
        roleRepository.save(member);
        Company other = companyRepository.save(new Company("Reconcile Two " + UUID.randomUUID()));
        Role otherAdmin = new Role(other, "Company Admin");
        otherAdmin.getTemplates().add(shared);
        roleRepository.save(otherAdmin);

        int added = reconciler.reconcile();

        assertThat(added).isPositive();
        int companyKeys = accessControlRepository.findByCompanyAssignableTrue().size();
        assertThat(adminOnly.getAccessControls()).hasSize(companyKeys);
        assertThat(shared.getAccessControls()).hasSize(1);
        // Running it again changes nothing.
        assertThat(reconciler.reconcile()).isZero();
    }
}
