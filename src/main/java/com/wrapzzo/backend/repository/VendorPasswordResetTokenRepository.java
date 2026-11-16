package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.VendorPasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorPasswordResetTokenRepository extends JpaRepository<VendorPasswordResetToken, UUID> {

    Optional<VendorPasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByVendor_IdAndCreatedAtAfter(UUID vendorId, Instant after);

    long countByVendor_IdAndCreatedAtAfter(UUID vendorId, Instant after);

    long countByCreatedAtAfter(Instant after);

    /** A vendor has one live link at a time: a new request ends the older ones (they stay as rows, for the day's count). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VendorPasswordResetToken t set t.expiresAt = :now where t.vendor.id = :vendorId and t.expiresAt > :now")
    int expireAllFor(@Param("vendorId") UUID vendorId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VendorPasswordResetToken t where t.vendor.id = :vendorId")
    int deleteAllFor(@Param("vendorId") UUID vendorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VendorPasswordResetToken t where t.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
