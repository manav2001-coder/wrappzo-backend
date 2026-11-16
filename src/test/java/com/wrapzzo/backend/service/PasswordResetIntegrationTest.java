package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.exception.InvalidResetTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.exception.WeakPasswordException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.PasswordResetTokenRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorPasswordResetTokenRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" against the real database, for a person and for a vendor: who is sent a link and who is quietly sent
 * nothing, the limits, that only a hash is kept, and that using the link ends every session and every other link, clears a
 * lockout, tells the person, and does not sign them in.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class PasswordResetIntegrationTest {

    private static final String OLD = "the-old-password";
    private static final String NEW = "a-brand-new-one";

    @Autowired private PasswordResetService users;
    @Autowired private VendorPasswordResetService vendors;
    @Autowired private AuthService authService;
    @Autowired private VendorAuthService vendorAuthService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private PasswordResetTokenRepository userTokens;
    @Autowired private VendorPasswordResetTokenRepository vendorTokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;
    @Autowired private ApplicationEvents events;

    private Company company;
    private User person;
    private Vendor vendor;

    @BeforeEach
    void seed() {
        company = companyRepository.save(new Company("Reset Test Co"));
        Role role = roleRepository.save(new Role(company, "AM"));
        person = userRepository.save(new User(company, role, "Rohit Sharma", "rohit-" + UUID.randomUUID() + "@test.com", passwordEncoder.encode(OLD)));
        vendor = vendorRepository.save(new Vendor("Sky Prints", "vendor-" + UUID.randomUUID() + "@test.com", null, passwordEncoder.encode(OLD), person));
    }

    /** Changes a row behind JPA's back: what is waiting is written first, and what is loaded is forgotten after, so the next read sees the change. */
    private void sql(String statement, Object... args) {
        em.flush();
        jdbc.update(statement, args);
        em.clear();
    }

    private PasswordResetRequestedEvent theLink() {
        var sent = events.stream(PasswordResetRequestedEvent.class).toList();
        assertThat(sent).hasSize(1);
        return sent.get(0);
    }

    private static String rawTokenOf(PasswordResetRequestedEvent event) {
        return event.resetUrl().substring(event.resetUrl().indexOf("token=") + 6);
    }

    // ---- asking for a link ----

    @Test
    void anActivePersonIsSentALinkAndOnlyItsHashIsKept() {
        users.requestReset(" " + person.getEmail().toUpperCase() + " ");

        PasswordResetRequestedEvent link = theLink();
        assertThat(link.email()).isEqualTo(person.getEmail());
        assertThat(link.resetUrl()).contains("/reset-password?token=");
        assertThat(link.expiryMinutes()).isEqualTo(30);
        String raw = rawTokenOf(link);
        assertThat(raw).hasSize(64);
        var stored = userTokens.findAll().get(0);
        assertThat(stored.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(stored.getExpiresAt()).isBetween(Instant.now().plus(Duration.ofMinutes(28)), Instant.now().plus(Duration.ofMinutes(31)));
    }

    @Test
    void nobodyIsToldWhetherAnEmailHasAnAccount_theUnknownOnesJustGetNothing() {
        users.requestReset("nobody-" + UUID.randomUUID() + "@test.com");
        assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();
        assertThat(userTokens.count()).isZero();
    }

    @Test
    void aSuspendedAccountAndADeactivatedCompanyGetNothing() {
        sql("update users set status = 'SUSPENDED' where id = ?", person.getId());
        users.requestReset(person.getEmail());
        sql("update users set status = 'ACTIVE' where id = ?", person.getId());
        sql("update companies set status = 'INACTIVE' where id = ?", company.getId());
        users.requestReset(person.getEmail());
        assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();
    }

    @Test
    void someoneWhoNeverSetUpIsSentASetupEmailInstead() {
        sql("update users set status = 'INVITED' where id = ?", person.getId());
        users.requestReset(person.getEmail());
        assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();
        assertThat(events.stream(UserInvitesIssuedEvent.class)).hasSize(1);
    }

    @Test
    void asecondRequestStraightAwayIsHeldOffAndADaysWorthIsCapped() {
        users.requestReset(person.getEmail());
        users.requestReset(person.getEmail());
        assertThat(events.stream(PasswordResetRequestedEvent.class)).hasSize(1);

        // Four more earlier today fill the day's five; a request now is refused even though the last one was long ago.
        sql("update password_reset_tokens set created_at = now() - interval '3 hours' where user_id = ?", person.getId());
        for (int i = 0; i < 4; i++) {
            sql("insert into password_reset_tokens (user_id, token_hash, expires_at, created_at) values (?, ?, now() - interval '1 hour', now() - interval '2 hours')", person.getId(), TokenHasher.sha256Hex("old-" + i));
        }
        users.requestReset(person.getEmail());
        assertThat(events.stream(PasswordResetRequestedEvent.class)).hasSize(1);
    }

    @Test
    void aNewRequestEndsTheOlderLink() {
        users.requestReset(person.getEmail());
        String first = rawTokenOf(theLink());
        sql("update password_reset_tokens set created_at = now() - interval '10 minutes' where user_id = ?", person.getId());
        users.requestReset(person.getEmail());

        assertThatThrownBy(() -> users.resetPassword(first, NEW, NEW)).isInstanceOf(InvalidResetTokenException.class);
    }

    // ---- using the link ----

    @Test
    void usingTheLinkChangesThePasswordSignsOutEverywhereClearsALockoutAndTellsThePerson() {
        TokenPair phone = authService.login(person.getEmail(), OLD);
        sql("update users set failed_login_attempts = 4, locked_until = now() + interval '10 minutes' where id = ?", person.getId());
        users.requestReset(person.getEmail());
        String raw = rawTokenOf(theLink());

        users.resetPassword(raw, NEW, NEW);

        assertThat(authService.login(person.getEmail(), NEW)).isNotNull();
        assertThatThrownBy(() -> authService.login(person.getEmail(), OLD)).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.refresh(phone.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(userRepository.findById(person.getId()).orElseThrow().getLockedUntil()).isNull();
        assertThat(events.stream(PasswordChangedEvent.class).toList()).hasSize(1).first().extracting(PasswordChangedEvent::email).isEqualTo(person.getEmail());
    }

    @Test
    void aLinkWorksOnceOnly_andUsingItEndsAnyOtherLinkToo() {
        users.requestReset(person.getEmail());
        String raw = rawTokenOf(theLink());
        users.resetPassword(raw, NEW, NEW);

        assertThat(userTokens.count()).isZero();
        assertThatThrownBy(() -> users.resetPassword(raw, "another-new-one", "another-new-one")).isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void anExpiredOrMadeUpLinkIsRefused() {
        users.requestReset(person.getEmail());
        String raw = rawTokenOf(theLink());
        sql("update password_reset_tokens set expires_at = now() - interval '1 minute' where user_id = ?", person.getId());

        assertThatThrownBy(() -> users.resetPassword(raw, NEW, NEW)).isInstanceOf(InvalidResetTokenException.class).hasMessageContaining("invalid, expired, or already used");
        assertThatThrownBy(() -> users.resetPassword("not-a-real-link", NEW, NEW)).isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void aMismatchOrAWeakPasswordIsRefusedAndTheLinkSurvivesForAnotherTry() {
        users.requestReset(person.getEmail());
        String raw = rawTokenOf(theLink());

        assertThatThrownBy(() -> users.resetPassword(raw, NEW, "something-else")).isInstanceOf(PasswordMismatchException.class);
        assertThatThrownBy(() -> users.resetPassword(raw, "short", "short")).isInstanceOf(WeakPasswordException.class);
        users.resetPassword(raw, NEW, NEW);
        assertThat(authService.login(person.getEmail(), NEW)).isNotNull();
    }

    @Test
    void aResetDoesNotReactivateASuspendedAccount() {
        users.requestReset(person.getEmail());
        String raw = rawTokenOf(theLink());
        sql("update users set status = 'SUSPENDED' where id = ?", person.getId());

        assertThatThrownBy(() -> users.resetPassword(raw, NEW, NEW)).isInstanceOf(InvalidResetTokenException.class);
        assertThat(userRepository.findById(person.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    // ---- a vendor ----

    @Test
    void aVendorGetsTheirOwnLinkOnTheVendorAddressAndResetsTheSameWay() {
        TokenPair other = vendorAuthService.login(vendor.getContactEmail(), OLD);
        vendors.requestReset(vendor.getContactEmail());
        PasswordResetRequestedEvent link = theLink();
        assertThat(link.resetUrl()).contains("/vendor/reset-password?token=");

        vendors.resetPassword(rawTokenOf(link), NEW, NEW);

        assertThat(vendorAuthService.login(vendor.getContactEmail(), NEW)).isNotNull();
        assertThatThrownBy(() -> vendorAuthService.login(vendor.getContactEmail(), OLD)).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> vendorAuthService.refresh(other.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(vendorTokens.count()).isZero();
        assertThat(events.stream(PasswordChangedEvent.class)).hasSize(1);
    }

    @Test
    void aSuspendedVendorOrAnUnknownOneGetsNothing_andAPersonsLinkIsNotAVendorsLink() {
        vendors.requestReset("nobody-" + UUID.randomUUID() + "@test.com");
        sql("update vendors set status = 'SUSPENDED' where id = ?", vendor.getId());
        vendors.requestReset(vendor.getContactEmail());
        assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();

        users.requestReset(person.getEmail());
        String personsLink = rawTokenOf(theLink());
        assertThatThrownBy(() -> vendors.resetPassword(personsLink, NEW, NEW)).isInstanceOf(InvalidResetTokenException.class);
    }
}
