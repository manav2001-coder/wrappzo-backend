package com.wrapzzo.backend.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserAccessControlId implements Serializable {

    private UUID userId;
    private UUID accessControlId;

    protected UserAccessControlId() {
    }

    public UserAccessControlId(UUID userId, UUID accessControlId) {
        this.userId = userId;
        this.accessControlId = accessControlId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccessControlId() {
        return accessControlId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserAccessControlId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(accessControlId, that.accessControlId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, accessControlId);
    }
}
