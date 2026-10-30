package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of access-control keys, optionally including other templates (their keys are
 * added to this one's, never removed). Roles and users are mapped to templates, and templates are
 * the only thing that grants. A null company means a system master template: shared by every
 * company, editable by platform staff only; a company may clone one or build its own.
 */
@Entity
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** The keys this template grants itself, before anything it includes. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "template_access_controls",
            joinColumns = @JoinColumn(name = "template_id"),
            inverseJoinColumns = @JoinColumn(name = "access_control_id"))
    private Set<AccessControl> accessControls = new HashSet<>();

    /** Templates this one includes. Kept acyclic and shallow by TemplateGraph before every write. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "template_references",
            joinColumns = @JoinColumn(name = "template_id"),
            inverseJoinColumns = @JoinColumn(name = "referenced_template_id"))
    private Set<Template> references = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Template(Company company, String name, String description) {
        this.company = company;
        this.name = name;
        this.description = description;
    }

    public boolean isSystem() {
        return company == null;
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
