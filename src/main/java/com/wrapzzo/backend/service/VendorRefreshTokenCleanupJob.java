package com.wrapzzo.backend.service;

import com.wrapzzo.backend.repository.VendorRefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps vendor_refresh_tokens from growing unbounded as sessions expire/rotate. */
@Component
@RequiredArgsConstructor
public class VendorRefreshTokenCleanupJob implements BackgroundJob {

    private final VendorRefreshTokenRepository vendorRefreshTokenRepository;

    @Override
    public String name() {
        return "vendor-refresh-token-cleanup";
    }

    @Override
    public String label() {
        return "Vendor refresh token cleanup";
    }

    @Override
    @Transactional
    public String run() {
        int deleted = vendorRefreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        return "Purged " + deleted + " expired or revoked vendor refresh tokens";
    }
}
