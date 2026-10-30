package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly display number ("CID-{companyNo}") -- DB-generated, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "company_no", insertable = false, updatable = false)
    private Integer companyNo;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    /** S3 object key, not a URL -- see the migration's comment. */
    @Column(name = "logo_key", length = 500)
    private String logoKey;

    /** With Senior Artwork Approval on: how many managers above the MR sign off on artwork, 1-5 (V44). */
    @Column(name = "artwork_approval_levels", nullable = false)
    private int artworkApprovalLevels = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Company(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
