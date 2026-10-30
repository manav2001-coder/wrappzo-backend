package com.wrapzzo.backend.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CompanyAccessControlId implements Serializable {

    private UUID companyId;
    private UUID accessControlId;

    protected CompanyAccessControlId() {
    }

    public CompanyAccessControlId(UUID companyId, UUID accessControlId) {
        this.companyId = companyId;
        this.accessControlId = accessControlId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompanyAccessControlId other
                && Objects.equals(companyId, other.companyId)
                && Objects.equals(accessControlId, other.accessControlId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, accessControlId);
    }
}
