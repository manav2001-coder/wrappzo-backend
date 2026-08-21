package com.wrapzzo.backend.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RoleAccessControlId implements Serializable {

    private UUID roleId;
    private UUID accessControlId;

    protected RoleAccessControlId() {
    }

    public RoleAccessControlId(UUID roleId, UUID accessControlId) {
        this.roleId = roleId;
        this.accessControlId = accessControlId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getAccessControlId() {
        return accessControlId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoleAccessControlId that)) {
            return false;
        }
        return Objects.equals(roleId, that.roleId) && Objects.equals(accessControlId, that.accessControlId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, accessControlId);
    }
}
