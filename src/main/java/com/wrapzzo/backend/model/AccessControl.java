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

    /** module.action, e.g. "campaign.create" -- one of security.Permission's keys. */
    @Column(nullable = false, unique = true)
    private String key;

    /** The part of the app it belongs to, e.g. "campaign" -- a row of the access grid. */
    @Column(nullable = false, length = 64)
    private String module;

    /** What it allows within that module, e.g. "create" -- a column of the access grid. */
    @Column(nullable = false, length = 64)
    private String action;

    /** The action's label, e.g. "Create". */
    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** The module's label, e.g. "Campaigns". */
    @Column(nullable = false)
    private String category;

    /** Order on the access screen: modules by hundreds, actions within them. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessControlType type;

    /** False for platform-wide keys (e.g. platform_order.manage) that must never be grantable to a single company's designation -- see DesignationAccessService. */
    @Column(name = "company_assignable", nullable = false)
    private boolean companyAssignable = true;

    /** Feature master switch: an inactive key grants nothing, wherever a template carries it. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AccessControl(String key, String name, String category, AccessControlType type) {
        int dot = key.indexOf('.');
        this.key = key;
        this.module = key.substring(0, dot);
        this.action = key.substring(dot + 1);
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
