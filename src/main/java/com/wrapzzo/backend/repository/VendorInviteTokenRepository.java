package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.VendorInviteToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorInviteTokenRepository extends JpaRepository<VendorInviteToken, UUID> {

    Optional<VendorInviteToken> findByTokenHash(String tokenHash);

    /** Called before issuing a fresh invite, so a vendor never has two simultaneously valid setup links. */
    @Modifying
    @Query("delete from VendorInviteToken t where t.vendor.id = :vendorId and t.usedAt is null")
    void deleteUnusedByVendorId(@Param("vendorId") UUID vendorId);
}
