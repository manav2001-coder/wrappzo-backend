package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.exception.AccountLockedException;
import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidInviteTokenException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import com.wrapzzo.backend.model.AccessEffect;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserInviteToken;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.TokenHasher;
import com.wrapzzo.backend.security.UserPrincipalClaims;
import com.wrapzzo.backend.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real login/refresh/logout/lockout flow and the Redis-backed
 * permission resolution end-to-end against the local docker-compose Postgres
 * + Redis. @Transactional rolls back every DB write after each test; Redis
 * keys are namespaced by freshly-generated UUIDs each run, so stale cache
 * entries never collide and simply expire on their own TTL.
 */
@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccessControlCacheService accessControlCacheService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccessControlRepository accessControlRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private FeatureService featureService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInviteTokenRepository userInviteTokenRepository;

    private User user;
    private AccessControl campaignCreate;
    private AccessControl billingView;

    @BeforeEach
    void seedFixtures() {
        Company company = companyRepository.save(new Company("Alembic Pharma"));
        Role role = roleRepository.save(new Role(company, "Campaign Manager"));

        campaignCreate = accessControlRepository.save(
                new AccessControl("test_reports.create", "Create Report", "Reports", AccessControlType.FUNCTIONAL));
        billingView = accessControlRepository.save(
                new AccessControl("billing.view", "View Billing", "Billing", AccessControlType.FUNCTIONAL));

        featureService.registerAll(company);
        Template roleTemplate = new Template(company, "Campaign Manager access", null);
        roleTemplate.getAccessControls().add(campaignCreate);
        role.getTemplates().add(templateRepository.save(roleTemplate));
        roleRepository.save(role);

        user = userRepository.save(
                new User(company, role, "Priya", "priya@alembic.com", passwordEncoder.encode(RAW_PASSWORD)));
    }

    @Test
    void loginIssuesTokensAndAccessTokenCarriesIdentityOnly() {
        TokenPair tokens = authService.login(user.getEmail(), RAW_PASSWORD);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        UserPrincipalClaims claims = (UserPrincipalClaims) jwtService.parseAccessToken(tokens.accessToken());
        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.roleId()).isEqualTo(user.getRole().getId());
    }

    @Test
    void loginWithWrongPasswordFails() {
        assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void accountLocksAfterTooManyFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong-password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(user.getEmail(), RAW_PASSWORD)).isInstanceOf(AccountLockedException.class);
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePreviousOne() {
        TokenPair firstTokens = authService.login(user.getEmail(), RAW_PASSWORD);

        TokenPair refreshedTokens = authService.refresh(firstTokens.refreshToken());
        assertThat(refreshedTokens.refreshToken()).isNotEqualTo(firstTokens.refreshToken());

        assertThatThrownBy(() -> authService.refresh(firstTokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void suspendedUserCannotRefreshEvenWithAValidToken() {
        TokenPair tokens = authService.login(user.getEmail(), RAW_PASSWORD);

        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TokenPair tokens = authService.login(user.getEmail(), RAW_PASSWORD);

        authService.logout(tokens.refreshToken());

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void setupPasswordActivatesAnInvitedUserAndIssuesTokens() {
        user.setStatus(UserStatus.INVITED);
        userRepository.save(user);
        String rawToken = TokenHasher.generateOpaqueToken();
        userInviteTokenRepository.save(
                new UserInviteToken(user, TokenHasher.sha256Hex(rawToken), Instant.now().plus(1, ChronoUnit.DAYS)));

        TokenPair tokens = authService.setupPassword(rawToken, "New@Password1", "New@Password1");

        assertThat(tokens.accessToken()).isNotBlank();
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(authService.login(user.getEmail(), "New@Password1").accessToken()).isNotBlank();
    }

    @Test
    void setupPasswordRejectsMismatchedConfirmation() {
        String rawToken = TokenHasher.generateOpaqueToken();
        userInviteTokenRepository.save(
                new UserInviteToken(user, TokenHasher.sha256Hex(rawToken), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> authService.setupPassword(rawToken, "New@Password1", "Different@1"))
                .isInstanceOf(PasswordMismatchException.class);
    }

    @Test
    void setupPasswordRejectsAnUnknownToken() {
        assertThatThrownBy(() -> authService.setupPassword("not-a-real-token", "New@Password1", "New@Password1"))
                .isInstanceOf(InvalidInviteTokenException.class);
    }

    @Test
    void effectivePermissionsCombineRoleTemplatesWithUserTemplatesAndFollowInclusions() {
        accessControlCacheService.invalidate(null);
        Set<String> initial = accessControlCacheService.resolveEffectiveKeys(user);
        assertThat(initial).containsExactly("test_reports.create");

        Template billing = new Template(user.getCompany(), "Billing access", null);
        billing.getAccessControls().add(billingView);
        user.getTemplates().add(templateRepository.save(billing));
        userRepository.save(user);
        accessControlCacheService.invalidate(null);

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactlyInAnyOrder("test_reports.create", "billing.view");

        user.getRole().getTemplates().clear();
        roleRepository.save(user.getRole());
        accessControlCacheService.invalidate(null);

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactly("billing.view");
    }
}
