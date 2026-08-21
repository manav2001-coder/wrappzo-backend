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
import lombok.Setter;

/** Baseline: which access-control keys a role grants. */
@Entity
@Table(name = "role_access_controls")
@Getter
@Setter
@NoArgsConstructor
public class RoleAccessControl {

    @EmbeddedId
    private RoleAccessControlId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("accessControlId")
    @JoinColumn(name = "access_control_id")
    private AccessControl accessControl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RoleAccessControl(Role role, AccessControl accessControl) {
        this.role = role;
        this.accessControl = accessControl;
        this.id = new RoleAccessControlId(role.getId(), accessControl.getId());
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
