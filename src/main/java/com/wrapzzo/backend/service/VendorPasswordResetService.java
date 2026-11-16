package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.InvalidResetTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorPasswordResetToken;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.PasswordResetTokenRepository;
import com.wrapzzo.backend.repository.VendorPasswordResetTokenRepository;
import com.wrapzzo.backend.repository.VendorRefreshTokenRepository;
import com.wrapzzo.backend.repository.VendorRepository;
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
 * "Forgot password" for a vendor, the counterpart of {@link PasswordResetService} for the separate vendor login: same one-use link,
 * same limits, same answer whether or not the account exists, and the vendor is signed out everywhere when they use it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorPasswordResetService {

    private static final Duration DAY = Duration.ofDays(1);

    private final VendorRepository vendorRepository;
    private final VendorPasswordResetTokenRepository tokens;
    private final PasswordResetTokenRepository userTokens;
    private final VendorRefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetSettings settings;
    private final ApplicationEventPublisher events;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public void requestReset(String rawEmail) {
        Instant now = Instant.now();
        tokens.deleteOlderThan(now.minus(Duration.ofDays(2)));
        Optional<Vendor> found = vendorRepository.findByContactEmailIgnoreCase(rawEmail.trim());
        if (found.isEmpty()) {
            return;
        }
        Vendor vendor = found.get();
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            return;
        }
        if (tooSoonOrTooMany(vendor, now) || !underGlobalCap(now)) {
            return;
        }
        tokens.expireAllFor(vendor.getId(), now);
        String raw = TokenHasher.generateOpaqueToken();
        tokens.save(new VendorPasswordResetToken(vendor, TokenHasher.sha256Hex(raw), now.plus(settings.expiry())));
        events.publishEvent(new PasswordResetRequestedEvent(vendor.getContactEmail(), vendor.getName(), frontendBaseUrl + "/vendor/reset-password?token=" + raw, settings.expiryMinutes()));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        VendorPasswordResetToken token = tokens.findByTokenHash(TokenHasher.sha256Hex(rawToken)).filter(VendorPasswordResetToken::isUsable).orElseThrow(InvalidResetTokenException::new);
        Vendor vendor = token.getVendor();
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new InvalidResetTokenException();
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException();
        }
        PasswordPolicy.require(newPassword, null);
        String encoded = passwordEncoder.encode(newPassword);
        java.util.UUID vendorId = vendor.getId();
        tokens.deleteAllFor(vendorId);
        refreshTokens.revokeAllFor(vendorId, Instant.now());
        Vendor fresh = vendorRepository.findById(vendorId).orElseThrow(InvalidResetTokenException::new);
        fresh.setPasswordHash(encoded);
        fresh.setFailedLoginAttempts((short) 0);
        fresh.setLockedUntil(null);
        events.publishEvent(new PasswordChangedEvent(fresh.getContactEmail(), fresh.getName(), Instant.now()));
    }

    private boolean tooSoonOrTooMany(Vendor vendor, Instant now) {
        return tokens.existsByVendor_IdAndCreatedAtAfter(vendor.getId(), now.minus(settings.cooldown()))
                || tokens.countByVendor_IdAndCreatedAtAfter(vendor.getId(), now.minus(DAY)) >= settings.maxPerDay();
    }

    private boolean underGlobalCap(Instant now) {
        long today = tokens.countByCreatedAtAfter(now.minus(DAY)) + userTokens.countByCreatedAtAfter(now.minus(DAY));
        if (today >= settings.globalDailyCap()) {
            log.warn("Password reset emails have reached the daily cap of {}; further requests are not sent today", settings.globalDailyCap());
            return false;
        }
        return true;
    }
}
