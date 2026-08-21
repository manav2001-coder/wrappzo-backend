package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.AccountLockedException;
import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidInviteTokenException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorInviteToken;
import com.wrapzzo.backend.model.VendorRefreshToken;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.VendorInviteTokenRepository;
import com.wrapzzo.backend.repository.VendorRefreshTokenRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.JwtService;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors AuthService's shape (Argon2, lockout, rotatable refresh tokens),
 * but deliberately does not share its tables, tokens, or code path -- see
 * Vendor's own doc comment for why that separation matters.
 */
@Service
public class VendorAuthService {

    private final VendorRepository vendorRepository;
    private final VendorRefreshTokenRepository vendorRefreshTokenRepository;
    private final VendorInviteTokenRepository vendorInviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpiryDays;
    private final int maxFailedLoginAttempts;
    private final long lockoutDurationMinutes;

    public VendorAuthService(
            VendorRepository vendorRepository,
            VendorRefreshTokenRepository vendorRefreshTokenRepository,
            VendorInviteTokenRepository vendorInviteTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${auth.refresh-token-expiry-days}") long refreshTokenExpiryDays,
            @Value("${auth.max-failed-login-attempts}") int maxFailedLoginAttempts,
            @Value("${auth.lockout-duration-minutes}") long lockoutDurationMinutes) {
        this.vendorRepository = vendorRepository;
        this.vendorRefreshTokenRepository = vendorRefreshTokenRepository;
        this.vendorInviteTokenRepository = vendorInviteTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Transactional
    public TokenPair login(String contactEmail, String rawPassword) {
        Vendor vendor = vendorRepository.findByContactEmail(contactEmail).orElseThrow(InvalidCredentialsException::new);

        if (vendor.getLockedUntil() != null && vendor.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(vendor.getLockedUntil());
        }

        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, vendor.getPasswordHash())) {
            registerFailedAttempt(vendor);
            throw new InvalidCredentialsException();
        }

        vendor.setFailedLoginAttempts((short) 0);
        vendor.setLockedUntil(null);
        return issueTokenPair(vendor);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        VendorRefreshToken existing = vendorRefreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(VendorRefreshToken::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        existing.setRevokedAt(Instant.now());

        Vendor vendor = existing.getVendor();
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }

        return issueTokenPair(vendor);
    }

    /** Consumes a single-use invite token, sets the vendor's real password, and logs them straight in. */
    @Transactional
    public TokenPair setupPassword(String rawToken, String password, String confirmPassword) {
        if (!Objects.equals(password, confirmPassword)) {
            throw new PasswordMismatchException();
        }

        VendorInviteToken invite = vendorInviteTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .filter(VendorInviteToken::isActive)
                .orElseThrow(InvalidInviteTokenException::new);
        invite.setUsedAt(Instant.now());

        Vendor vendor = invite.getVendor();
        vendor.setPasswordHash(passwordEncoder.encode(password));
        vendor.setFailedLoginAttempts((short) 0);
        vendor.setLockedUntil(null);
        return issueTokenPair(vendor);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        vendorRefreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private TokenPair issueTokenPair(Vendor vendor) {
        String accessToken = jwtService.generateVendorAccessToken(vendor);
        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenExpiryDays));
        vendorRefreshTokenRepository.save(new VendorRefreshToken(vendor, TokenHasher.sha256Hex(rawRefreshToken), expiresAt));
        return new TokenPair(accessToken, rawRefreshToken, jwtService.getAccessTokenExpiry().toSeconds());
    }

    private void registerFailedAttempt(Vendor vendor) {
        short attempts = (short) (vendor.getFailedLoginAttempts() + 1);
        vendor.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedLoginAttempts) {
            vendor.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockoutDurationMinutes)));
        }
    }
}
