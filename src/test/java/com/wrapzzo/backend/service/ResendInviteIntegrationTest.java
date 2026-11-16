package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.SetupLinkResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Resend invite" and "Copy setup link", against the real database: only someone who has not set up yet can be sent one, the
 * old link stops working, a second tap straight away is held off, and only someone who sees the whole company may take a link.
 */
@SpringBootTest
@Transactional
class ResendInviteIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserInviteTokenRepository tokens;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Company company;
    private Role role;
    private User pending;

    @BeforeEach
    void seed() {
        company = companyRepository.save(new Company("Resend Test Pharma"));
        role = roleRepository.save(new Role(company, "AM"));
        pending = userRepository.save(new User(company, role, "Santosh Kumar", "santosh-" + UUID.randomUUID() + "@test.com", "n/a"));
        pending.setStatus(UserStatus.INVITED);
        actAs(userRepository.save(new User(null, roleRepository.save(new Role(null, "Platform " + UUID.randomUUID())), "Wrappzo Admin", "admin-" + UUID.randomUUID() + "@wrapzzo.com", "n/a")));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(User caller) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller.getId(), null, List.of()));
    }

    private long liveLinks(User user) {
        return tokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId()) && t.isActive()).count();
    }

    @Test
    void aResendLeavesOneLiveLinkAndReplacesTheOldOne() {
        String oldRaw = TokenHasher.generateOpaqueToken();
        tokens.save(new com.wrapzzo.backend.model.UserInviteToken(pending, TokenHasher.sha256Hex(oldRaw), Instant.now().plus(Duration.ofDays(1))));
        tokens.flush();
        // An hour-old link, so the cooldown does not apply (a token's creation time is not something the app can change).
        jdbc.update("update user_invite_tokens set created_at = now() - interval '1 hour' where user_id = ?", pending.getId());

        userService.resendInvite(company.getId(), pending.getId());

        assertThat(liveLinks(pending)).isEqualTo(1);
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex(oldRaw))).isEmpty();
    }

    @Test
    void aSecondResendStraightAwayIsHeldOffWithAReason() {
        userService.resendInvite(company.getId(), pending.getId());
        assertThatThrownBy(() -> userService.resendInvite(company.getId(), pending.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("a moment ago");
        assertThat(liveLinks(pending)).isEqualTo(1);
    }

    @Test
    void someoneAlreadySetUpHasNothingToResend() {
        pending.setStatus(UserStatus.ACTIVE);
        assertThatThrownBy(() -> userService.resendInvite(company.getId(), pending.getId())).isInstanceOf(ConflictException.class).hasMessageContaining("already set up");
        assertThatThrownBy(() -> userService.issueSetupLink(company.getId(), pending.getId())).isInstanceOf(ConflictException.class).hasMessageContaining("already set up");
    }

    @Test
    void aSetupLinkIsHandedBackNotEmailedAndWorksOnceForTheUsualTime() {
        SetupLinkResponse link = userService.issueSetupLink(company.getId(), pending.getId());

        assertThat(link.setupUrl()).contains("/setup-password?token=");
        String raw = link.setupUrl().substring(link.setupUrl().indexOf("token=") + 6);
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex(raw))).isPresent().get().extracting(t -> t.isActive()).isEqualTo(true);
        assertThat(link.expiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(liveLinks(pending)).isEqualTo(1);
    }

    @Test
    void aPersonWhoDoesNotSeeTheWholeCompanyCannotTakeALinkForSomeoneElse() {
        User zonalManager = userRepository.save(new User(company, role, "Zonal Manager", "zm-" + UUID.randomUUID() + "@test.com", "n/a"));
        zonalManager.setCompanyWide(false);
        actAs(zonalManager);
        assertThatThrownBy(() -> userService.issueSetupLink(company.getId(), pending.getId())).isInstanceOf(AccessDeniedException.class);
    }
}
