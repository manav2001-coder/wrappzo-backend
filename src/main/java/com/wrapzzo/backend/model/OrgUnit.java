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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A generic, company-configurable hierarchy node (Division/Zone/Region/Area
 * or whatever a given company calls its levels). Optional per company.
 */
@Entity
@Table(name = "org_units")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    @Column(nullable = false)
    private String name;

    @Column(name = "level_name", nullable = false)
    private String levelName;

    /**
     * Every ancestor's id, root-first, excluding this row itself. Maintained
     * at creation time (parent's ancestorIds + parent's own id) so "everyone
     * under node X" is a single indexed query at any tree depth.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ancestor_ids", columnDefinition = "uuid[]", nullable = false)
    private List<UUID> ancestorIds = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrgUnit(Company company, OrgUnit parent, String name, String levelName) {
        this.company = company;
        this.parent = parent;
        this.name = name;
        this.levelName = levelName;
        this.ancestorIds = new ArrayList<>();
        if (parent != null) {
            this.ancestorIds.addAll(parent.getAncestorIds());
            this.ancestorIds.add(parent.getId());
        }
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
