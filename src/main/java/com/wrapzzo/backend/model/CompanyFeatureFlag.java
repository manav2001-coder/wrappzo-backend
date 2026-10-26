package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One company's on/off state for one CompanyFeature. Absence of a row (see repository) means off. */
@Entity
@Table(name = "company_feature_flags")
@Getter
@Setter
@NoArgsConstructor
public class CompanyFeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_key", nullable = false, length = 64)
    private CompanyFeature flagKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanyFeatureFlag(Company company, CompanyFeature flagKey, boolean enabled) {
        this.company = company;
        this.flagKey = flagKey;
        this.enabled = enabled;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }
}
