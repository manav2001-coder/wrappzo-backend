package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * A per-user override on top of the user's role baseline. GRANT adds a key
 * the role doesn't give them; REVOKE takes away a key the role would
 * otherwise grant. REVOKE always wins over the role baseline.
 */
@Entity
@Table(name = "user_access_controls")
@Getter
@Setter
@NoArgsConstructor
public class UserAccessControl {

    @EmbeddedId
    private UserAccessControlId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("accessControlId")
    @JoinColumn(name = "access_control_id")
    private AccessControl accessControl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessEffect effect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserAccessControl(User user, AccessControl accessControl, AccessEffect effect, User grantedBy) {
        this.user = user;
        this.accessControl = accessControl;
        this.effect = effect;
        this.grantedBy = grantedBy;
        this.id = new UserAccessControlId(user.getId(), accessControl.getId());
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
