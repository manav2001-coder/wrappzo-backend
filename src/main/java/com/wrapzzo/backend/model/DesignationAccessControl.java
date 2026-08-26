package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Which access-control keys a company's designation grants -- independent of and additive to
 * Role/role_access_controls (see AccessControlCacheService.resolveEffectiveKeys, which unions
 * the two). designation is the free-text users.designation value, not a separate entity.
 */
@Entity
@Table(name = "designation_access_controls")
@Getter
@NoArgsConstructor
public class DesignationAccessControl {

    @EmbeddedId
    private DesignationAccessControlId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("companyId")
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("accessControlId")
    @JoinColumn(name = "access_control_id")
    private AccessControl accessControl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DesignationAccessControl(Company company, String designation, AccessControl accessControl) {
        this.company = company;
        this.accessControl = accessControl;
        this.id = new DesignationAccessControlId(company.getId(), designation, accessControl.getId());
    }

    public String getDesignation() {
        return id.getDesignation();
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
