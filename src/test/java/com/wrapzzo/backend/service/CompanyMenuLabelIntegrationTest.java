package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** A company's own menu names: replace-all, cleaned, scoped to the caller's company, and on /api/auth/me. */
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
        actAs(alembicAdmin);
        Map<String, String> saved = companyMenuLabelService.replace(Map.of(
                "quick-gift", "  Request   Prescription Pads ",
                "gift-store", "Prescription Pads"));
        assertThat(saved).containsEntry("quick-gift", "Request Prescription Pads").containsEntry("gift-store", "Prescription Pads");
        assertThat(authService.getCurrentUser().menuLabels()).isEqualTo(saved);

        actAs(otherCompanyUser);
        assertThat(authService.getCurrentUser().menuLabels()).isEmpty();
    }

    @Test
    void aBlankOrOmittedNameGoesBackToTheDefault() {
        actAs(alembicAdmin);
        companyMenuLabelService.replace(Map.of("quick-gift", "Request Prescription Pads", "gift-store", "Prescription Pads"));
        Map<String, String> next = new HashMap<>();
        next.put("quick-gift", "   ");
        assertThat(companyMenuLabelService.replace(next)).isEmpty();
        assertThat(companyMenuLabelService.listOwn()).isEmpty();
    }

    @Test
    void badMenuIdsAndOverlongNamesAreRefused() {
        actAs(alembicAdmin);
        assertThatThrownBy(() -> companyMenuLabelService.replace(Map.of("Quick Gift!", "x"))).isInstanceOf(InvalidMenuLabelException.class);
        assertThatThrownBy(() -> companyMenuLabelService.replace(Map.of("quick-gift", "x".repeat(61)))).isInstanceOf(InvalidMenuLabelException.class);
    }
}
