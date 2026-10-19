package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which vendors serve a company -- scopes the Gift Store / Quick Gift catalog (GiftBrowseController) to that roster. */
@Entity
@Table(name = "company_vendors")
@Getter
@Setter
@NoArgsConstructor
public class CompanyVendor {

    @EmbeddedId
    private CompanyVendorId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("companyId")
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("vendorId")
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CompanyVendor(Company company, Vendor vendor) {
        this.company = company;
        this.vendor = vendor;
        this.id = new CompanyVendorId(company.getId(), vendor.getId());
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
