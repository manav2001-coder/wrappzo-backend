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

/**
 * The fixed, well-defined catalog of grantable capabilities. Roles and
 * per-user overrides reference this catalog by id — the catalog itself only
 * grows when a genuinely new capability is added to the product.
 */
@Entity
@Table(name = "access_controls")
@Getter
@Setter
@NoArgsConstructor
public class AccessControl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Machine-readable, e.g. "campaign.create" or "nav.billing". */
    @Column(nullable = false, unique = true)
    private String key;

    /** Human-readable label shown in the assignment UI, e.g. "Create Campaign". */
    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** Groups keys in the assignment UI, e.g. "Campaign", "Navigation". */
    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessControlType type;

    /** False for platform-wide keys (e.g. order.manage) that must never be grantable to a single company's designation -- see DesignationAccessService. */
    @Column(name = "company_assignable", nullable = false)
    private boolean companyAssignable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AccessControl(String key, String name, String category, AccessControlType type) {
        this.key = key;
        this.name = name;
        this.category = category;
        this.type = type;
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
