package com.wrapzzo.backend.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CompanyVendorId implements Serializable {

    private UUID companyId;
    private UUID vendorId;

    protected CompanyVendorId() {
    }

    public CompanyVendorId(UUID companyId, UUID vendorId) {
        this.companyId = companyId;
        this.vendorId = vendorId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompanyVendorId that)) {
            return false;
        }
        return Objects.equals(companyId, that.companyId) && Objects.equals(vendorId, that.vendorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, vendorId);
    }
}
