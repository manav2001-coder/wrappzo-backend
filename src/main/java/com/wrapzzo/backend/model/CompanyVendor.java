package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which vendors serve a company -- scopes the Gift Store / Quick Gift catalog
 * (GiftBrowseController) to that roster. orgUnit is null for a company-wide mapping
 * (visible to every org unit), or set to scope a vendor to one org unit and everything
 * beneath it (e.g. a vendor that only serves "Zone North") -- see
 * GiftService.listActiveForUser, which resolves visibility via OrgUnit.ancestorIds. A
 * surrogate id (not (company_id, vendor_id)) because the same vendor can now be mapped
 * more than once -- company-wide and/or to one or more specific org units.
 */
@Entity
@Table(name = "company_vendors")
@Getter
@Setter
@NoArgsConstructor
public class CompanyVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** Null = company-wide. Set = scoped to this org unit and its descendants. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CompanyVendor(Company company, Vendor vendor, OrgUnit orgUnit) {
        this.company = company;
        this.vendor = vendor;
        this.orgUnit = orgUnit;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
