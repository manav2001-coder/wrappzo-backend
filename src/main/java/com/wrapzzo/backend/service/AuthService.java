package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CurrentUserResponse;
import com.wrapzzo.backend.exception.AccountLockedException;
import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidInviteTokenException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.RefreshToken;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserInviteToken;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.RefreshTokenRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.JwtService;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserInviteTokenRepository userInviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessControlCacheService accessControlCacheService;
    private final long refreshTokenExpiryDays;
    private final int maxFailedLoginAttempts;
    private final long lockoutDurationMinutes;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserInviteTokenRepository userInviteTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AccessControlCacheService accessControlCacheService,
            @Value("${auth.refresh-token-expiry-days}") long refreshTokenExpiryDays,
            @Value("${auth.max-failed-login-attempts}") int maxFailedLoginAttempts,
            @Value("${auth.lockout-duration-minutes}") long lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userInviteTokenRepository = userInviteTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessControlCacheService = accessControlCacheService;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    /** Lets the frontend gate UI (e.g. hiding "Create campaign" from a Member) on the caller's real access-control keys, not a guessed role name. */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        UUID userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        return new CurrentUserResponse(
                accessControlCacheService.resolveEffectiveKeys(user),
                user.getCompany() != null ? user.getCompany().getId() : null,
                user.getCompany() != null ? user.getCompany().getName() : null);
    }

    @Transactional
    public TokenPair login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        return issueTokenPair(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        existing.setRevokedAt(Instant.now());

        User user = existing.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Revoked above regardless -- a suspended user's refresh token must never be usable again.
            throw new InvalidRefreshTokenException();
        }

        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public TokenPair setupPassword(String rawToken, String password, String confirmPassword) {
        if (!Objects.equals(password, confirmPassword)) {
            throw new PasswordMismatchException();
        }

        UserInviteToken invite = userInviteTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .filter(UserInviteToken::isActive)
                .orElseThrow(InvalidInviteTokenException::new);
        invite.setUsedAt(Instant.now());

        User user = invite.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        return issueTokenPair(user);
    }

    private TokenPair issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenExpiryDays));
        refreshTokenRepository.save(new RefreshToken(user, TokenHasher.sha256Hex(rawRefreshToken), expiresAt));
        return new TokenPair(accessToken, rawRefreshToken, jwtService.getAccessTokenExpiry().toSeconds());
    }

    private void registerFailedAttempt(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedLoginAttempts) {
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockoutDurationMinutes)));
        }
    }
}
