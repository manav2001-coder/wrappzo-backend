package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.InvalidMenuLabelException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** A company's menu names, set by platform staff: replace-all, cleaned, scoped to one company, and on /api/auth/me. */
@SpringBootTest
@Transactional
class CompanyMenuLabelIntegrationTest {

    @Autowired private CompanyMenuLabelService companyMenuLabelService;
    @Autowired private AuthService authService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private User alembicAdmin;
    private User otherCompanyUser;

    @BeforeEach
    void setUp() {
        alembicAdmin = saveUser(companyRepository.save(new Company("Menu Test Pharma " + UUID.randomUUID())));
        otherCompanyUser = saveUser(companyRepository.save(new Company("Other Pharma " + UUID.randomUUID())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User saveUser(Company company) {
        Role role = roleRepository.save(new Role(company, "Company Admin"));
        return userRepository.save(new User(company, role, "Admin", "menu-" + UUID.randomUUID() + "@test.com", "n/a"));
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    @Test
    void renamedMenusReachEveryoneInTheCompanyAndNoOneElse() {
        UUID alembic = alembicAdmin.getCompany().getId();
        Map<String, String> saved = companyMenuLabelService.replace(alembic, Map.of(
                "quick-gift", "  Request   Prescription Pads ",
                "gift-store", "Prescription Pads"));
        assertThat(saved).containsEntry("quick-gift", "Request Prescription Pads").containsEntry("gift-store", "Prescription Pads");
        actAs(alembicAdmin);
        assertThat(authService.getCurrentUser().menuLabels()).isEqualTo(saved);

        actAs(otherCompanyUser);
        assertThat(authService.getCurrentUser().menuLabels()).isEmpty();
    }

    @Test
    void aBlankOrOmittedNameGoesBackToTheDefault() {
        UUID alembic = alembicAdmin.getCompany().getId();
        companyMenuLabelService.replace(alembic, Map.of("quick-gift", "Request Prescription Pads", "gift-store", "Prescription Pads"));
        Map<String, String> next = new HashMap<>();
        next.put("quick-gift", "   ");
        assertThat(companyMenuLabelService.replace(alembic, next)).isEmpty();
        assertThat(companyMenuLabelService.list(alembic)).isEmpty();
    }

    @Test
    void badMenuIdsAndOverlongNamesAreRefused() {
        UUID alembic = alembicAdmin.getCompany().getId();
        assertThatThrownBy(() -> companyMenuLabelService.replace(alembic, Map.of("Quick Gift!", "x"))).isInstanceOf(InvalidMenuLabelException.class);
        assertThatThrownBy(() -> companyMenuLabelService.replace(alembic, Map.of("quick-gift", "x".repeat(61)))).isInstanceOf(InvalidMenuLabelException.class);
    }

    @Test
    void anUnknownCompanyIsRefused() {
        assertThatThrownBy(() -> companyMenuLabelService.replace(UUID.randomUUID(), Map.of("quick-gift", "x"))).isInstanceOf(CompanyNotFoundException.class);
        assertThatThrownBy(() -> companyMenuLabelService.list(UUID.randomUUID())).isInstanceOf(CompanyNotFoundException.class);
    }
}
