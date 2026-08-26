package com.wrapzzo.backend.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DesignationAccessControlId implements Serializable {

    private UUID companyId;
    private String designation;
    private UUID accessControlId;

    protected DesignationAccessControlId() {
    }

    public DesignationAccessControlId(UUID companyId, String designation, UUID accessControlId) {
        this.companyId = companyId;
        this.designation = designation;
        this.accessControlId = accessControlId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getDesignation() {
        return designation;
    }

    public UUID getAccessControlId() {
        return accessControlId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DesignationAccessControlId that)) {
            return false;
        }
        return Objects.equals(companyId, that.companyId)
                && Objects.equals(designation, that.designation)
                && Objects.equals(accessControlId, that.accessControlId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, designation, accessControlId);
    }
}
