package com.wrapzzo.backend.service;

import com.wrapzzo.backend.repository.VendorRefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps vendor_refresh_tokens from growing unbounded as sessions expire/rotate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VendorRefreshTokenCleanupJob {

    private final VendorRefreshTokenRepository vendorRefreshTokenRepository;

    @Scheduled(cron = "0 5 3 * * *")
    @Transactional
    public void purgeExpiredOrRevoked() {
        int deleted = vendorRefreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.info("Purged {} expired/revoked vendor refresh tokens", deleted);
    }
}
