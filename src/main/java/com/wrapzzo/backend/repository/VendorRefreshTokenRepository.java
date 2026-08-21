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

    @Modifying
    @Query("delete from VendorRefreshToken t where t.expiresAt < :cutoff or t.revokedAt is not null")
    int deleteExpiredOrRevoked(@Param("cutoff") Instant cutoff);
}
