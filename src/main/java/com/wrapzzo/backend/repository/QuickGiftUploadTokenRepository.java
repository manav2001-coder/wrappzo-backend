package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.QuickGiftUploadToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuickGiftUploadTokenRepository extends JpaRepository<QuickGiftUploadToken, UUID> {

    Optional<QuickGiftUploadToken> findByTokenHash(String tokenHash);

    /** Every token ever issued for this request -- used to retire older links when a fresh one is sent. */
    List<QuickGiftUploadToken> findByQuickGiftRequestId(UUID quickGiftRequestId);
}
