package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.exception.AccountLockedException;
import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.JwtService;
import com.wrapzzo.backend.security.VendorPrincipalClaims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors AuthServiceIntegrationTest's shape -- proves Vendor auth works end-to-end, fully independent of User auth. */
@SpringBootTest
@Transactional
class VendorAuthServiceIntegrationTest {

    private static final String RAW_PASSWORD = "vendor-correct-password";

    @Autowired
    private VendorAuthService vendorAuthService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    private Vendor vendor;

    @BeforeEach
    void seedVendor() {
        Company company = companyRepository.save(new Company("Vendor Auth Test Co"));
        Role role = roleRepository.save(new Role(company, "Whatever Role"));
        User creator = userRepository.save(new User(company, role, "Creator", "vendor-auth-creator-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        vendor = vendorRepository.save(
                new Vendor("Auth Test Vendor", "vendor-auth-" + UUID.randomUUID() + "@example.com", null, passwordEncoder.encode(RAW_PASSWORD), creator));
    }

    @Test
    void loginIssuesAVendorTypedToken() {
        TokenPair tokens = vendorAuthService.login(vendor.getContactEmail(), RAW_PASSWORD);

        var claims = jwtService.parseAccessToken(tokens.accessToken());
        assertThat(claims).isInstanceOf(VendorPrincipalClaims.class);
        assertThat(((VendorPrincipalClaims) claims).vendorId()).isEqualTo(vendor.getId());
    }

    @Test
    void loginWithWrongPasswordFails() {
        assertThatThrownBy(() -> vendorAuthService.login(vendor.getContactEmail(), "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void accountLocksAfterTooManyFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> vendorAuthService.login(vendor.getContactEmail(), "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> vendorAuthService.login(vendor.getContactEmail(), RAW_PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePreviousOne() {
        TokenPair firstTokens = vendorAuthService.login(vendor.getContactEmail(), RAW_PASSWORD);

        TokenPair refreshedTokens = vendorAuthService.refresh(firstTokens.refreshToken());
        assertThat(refreshedTokens.refreshToken()).isNotEqualTo(firstTokens.refreshToken());

        assertThatThrownBy(() -> vendorAuthService.refresh(firstTokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void suspendedVendorCannotRefreshEvenWithAValidToken() {
        TokenPair tokens = vendorAuthService.login(vendor.getContactEmail(), RAW_PASSWORD);

        vendor.setStatus(VendorStatus.SUSPENDED);
        vendorRepository.save(vendor);

        assertThatThrownBy(() -> vendorAuthService.refresh(tokens.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TokenPair tokens = vendorAuthService.login(vendor.getContactEmail(), RAW_PASSWORD);

        vendorAuthService.logout(tokens.refreshToken());

        assertThatThrownBy(() -> vendorAuthService.refresh(tokens.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
