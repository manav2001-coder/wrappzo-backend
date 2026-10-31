package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of access-control keys. A null company means a system-wide
 * (platform) role, e.g. Super Admin; otherwise the role belongs to exactly
 * one company.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditNames {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** The one platform Super Admin role -- gets every FUNCTIONAL access-control key dynamically (see AccessControlCacheService), not an explicit grant list that has to be kept in sync with every new module. */
    @Column(name = "is_platform_super_admin", nullable = false)
    private boolean platformSuperAdmin = false;

    /** The templates this role carries -- the only source of what its holders may do. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_templates", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "template_id"))
    private Set<Template> templates = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Role(Company company, String name) {
        this.company = company;
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
