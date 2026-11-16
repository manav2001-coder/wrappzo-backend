package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByUser_IdAndCreatedAtAfter(UUID userId, Instant after);

    long countByUser_IdAndCreatedAtAfter(UUID userId, Instant after);

    long countByCreatedAtAfter(Instant after);

    /** A person has one live link at a time: a new request ends the older ones (they stay as rows, for the day's count). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.expiresAt = :now where t.user.id = :userId and t.expiresAt > :now")
    int expireAllFor(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    int deleteAllFor(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasswordResetToken t where t.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
