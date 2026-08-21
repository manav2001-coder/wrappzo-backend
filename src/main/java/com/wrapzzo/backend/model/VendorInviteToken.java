package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Same shape as VendorRefreshToken, but single-use (usedAt) rather than revocable -- see Vendor's onboarding flow. */
@Entity
@Table(name = "vendor_invite_tokens")
@Getter
@Setter
@NoArgsConstructor
public class VendorInviteToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public VendorInviteToken(Vendor vendor, String tokenHash, Instant expiresAt) {
        this.vendor = vendor;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
