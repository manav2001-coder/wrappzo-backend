package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.InvalidResetTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.PasswordResetToken;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.PasswordResetTokenRepository;
import com.wrapzzo.backend.repository.RefreshTokenRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorPasswordResetTokenRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" for a person. They ask with their email; if there is an account that can use it, a one-use link is emailed.
 * Whatever happens, the caller is told the same thing (see {@link PasswordResetDispatcher}), so the page cannot be used to find
 * out who has an account.
 *
 * <ul>
 *   <li>Only a hash of the link is stored; the link itself exists only in the email.
 *   <li>An account that was never set up is sent a fresh <em>setup</em> email instead: that is what it needs.
 *   <li>A suspended account, or one in a deactivated company, gets nothing.
 *   <li>Limits: one per cooldown and a few a day for each person, and a cap on all of them together, so it cannot flood an inbox or use up our sending allowance.
 *   <li>Using the link signs the person out of every device, ends every other link, and tells them by email. It does not sign them in: they do that themselves.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration DAY = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokens;
    private final VendorPasswordResetTokenRepository vendorTokens;
    private final UserInviteTokenRepository inviteTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetSettings settings;
    private final UserService userService;
    private final ApplicationEventPublisher events;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Quietly does nothing for an email that cannot be helped; see the class comment for what it does otherwise. Returns whether
     * the email belongs to a person at all, whether or not a link was sent -- so the caller knows to look among vendors if not.
     */
    @Transactional
    public boolean requestReset(String rawEmail) {
        Instant now = Instant.now();
        tokens.deleteOlderThan(now.minus(Duration.ofDays(2)));
        Optional<User> found = userRepository.findByEmailIgnoreCase(rawEmail.trim());
        if (found.isEmpty()) {
            return false;
        }
        User user = found.get();
        if (user.getCompany() != null && user.getCompany().getStatus() == CompanyStatus.INACTIVE) {
            return true;
        }
        if (user.getStatus() == UserStatus.INVITED) {
            if (!inviteTokens.existsByUser_IdAndCreatedAtAfter(user.getId(), now.minus(settings.cooldown())) && underGlobalCap(now)) {
                userService.sendSetupInviteTo(user);
            }
            return true;
        }
        if (user.getStatus() != UserStatus.ACTIVE || tooSoonOrTooMany(user, now) || !underGlobalCap(now)) {
            return true;
        }
        tokens.expireAllFor(user.getId(), now);
        String raw = TokenHasher.generateOpaqueToken();
        tokens.save(new PasswordResetToken(user, TokenHasher.sha256Hex(raw), now.plus(settings.expiry())));
        events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getName(), frontendBaseUrl + "/reset-password?token=" + raw, settings.expiryMinutes()));
        return true;
    }

    /** Sets the new password for the person the link belongs to; everything else about their account stays as it was. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        PasswordResetToken token = tokens.findByTokenHash(TokenHasher.sha256Hex(rawToken)).filter(PasswordResetToken::isUsable).orElseThrow(InvalidResetTokenException::new);
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidResetTokenException();
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException();
        }
        PasswordPolicy.require(newPassword, null);
        String encoded = passwordEncoder.encode(newPassword);
        // The link is gone the moment it is used, and so is every other one. The bulk calls clear what is loaded, so the person is read again after them.
        java.util.UUID userId = user.getId();
        tokens.deleteAllFor(userId);
        refreshTokens.revokeAllFor(userId, Instant.now());
        User fresh = userRepository.findById(userId).orElseThrow(InvalidResetTokenException::new);
        fresh.setPasswordHash(encoded);
        fresh.setFailedLoginAttempts((short) 0);
        fresh.setLockedUntil(null);
        events.publishEvent(new PasswordChangedEvent(fresh.getEmail(), fresh.getName(), Instant.now()));
    }

    private boolean tooSoonOrTooMany(User user, Instant now) {
        return tokens.existsByUser_IdAndCreatedAtAfter(user.getId(), now.minus(settings.cooldown()))
                || tokens.countByUser_IdAndCreatedAtAfter(user.getId(), now.minus(DAY)) >= settings.maxPerDay();
    }

    private boolean underGlobalCap(Instant now) {
        long today = tokens.countByCreatedAtAfter(now.minus(DAY)) + vendorTokens.countByCreatedAtAfter(now.minus(DAY));
        if (today >= settings.globalDailyCap()) {
            log.warn("Password reset emails have reached the daily cap of {}; further requests are not sent today", settings.globalDailyCap());
            return false;
        }
        return true;
    }
}
