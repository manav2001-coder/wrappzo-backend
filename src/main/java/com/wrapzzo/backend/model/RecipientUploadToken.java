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

/** Same shape as VendorInviteToken/UserInviteToken -- a single-use, expiring link handed to an unauthenticated recipient so they can upload their own photo against a Submission approved without one. */
@Entity
@Table(name = "recipient_upload_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RecipientUploadToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RecipientUploadToken(Submission submission, String tokenHash, Instant expiresAt) {
        this.submission = submission;
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
