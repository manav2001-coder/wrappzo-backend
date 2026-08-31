package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.RecipientUploadToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientUploadTokenRepository extends JpaRepository<RecipientUploadToken, UUID> {

    Optional<RecipientUploadToken> findByTokenHash(String tokenHash);

    /** Every token ever issued for this submission -- used to retire older links when a fresh one is sent. */
    List<RecipientUploadToken> findBySubmissionId(UUID submissionId);
}
