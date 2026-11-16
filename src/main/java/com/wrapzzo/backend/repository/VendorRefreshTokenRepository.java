package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.VendorRefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorRefreshTokenRepository extends JpaRepository<VendorRefreshToken, UUID> {

    Optional<VendorRefreshToken> findByTokenHash(String tokenHash);

    /** Signs the vendor out everywhere: every refresh token they hold stops working. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VendorRefreshToken t set t.revokedAt = :now where t.vendor.id = :vendorId and t.revokedAt is null")
    int revokeAllFor(@Param("vendorId") UUID vendorId, @Param("now") Instant now);

    @Modifying
    @Query("delete from VendorRefreshToken t where t.expiresAt < :cutoff or t.revokedAt is not null")
    int deleteExpiredOrRevoked(@Param("cutoff") Instant cutoff);
}
