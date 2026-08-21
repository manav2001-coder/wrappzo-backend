package com.wrapzzo.backend.service;

import com.wrapzzo.backend.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps refresh_tokens from growing unbounded as sessions expire/rotate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredOrRevoked() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.info("Purged {} expired/revoked refresh tokens", deleted);
    }
}
