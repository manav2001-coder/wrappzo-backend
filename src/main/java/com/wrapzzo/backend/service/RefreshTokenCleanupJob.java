package com.wrapzzo.backend.service;

import com.wrapzzo.backend.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps refresh_tokens from growing unbounded as sessions expire/rotate. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob implements BackgroundJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public String name() {
        return "refresh-token-cleanup";
    }

    @Override
    public String label() {
        return "Refresh token cleanup";
    }

    @Override
    @Transactional
    public String run() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        return "Purged " + deleted + " expired or revoked refresh tokens";
    }
}
