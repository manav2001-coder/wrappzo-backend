package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A permission key registered for one company, and whether it is switched on there (the feature master). */
@Entity
@Table(name = "company_access_controls")
@Getter
@Setter
@NoArgsConstructor
public class CompanyAccessControl {

    @EmbeddedId
    private CompanyAccessControlId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("companyId")
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("accessControlId")
    @JoinColumn(name = "access_control_id")
    private AccessControl accessControl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public CompanyAccessControl(Company company, AccessControl accessControl) {
        this.company = company;
        this.accessControl = accessControl;
        this.id = new CompanyAccessControlId(company.getId(), accessControl.getId());
    }
}
