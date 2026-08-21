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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** A catalogue entry in the global, platform-owned gift store. No pricing/vendor columns yet -- see the migration's comment. */
@Entity
@Table(name = "gifts")
@Getter
@Setter
@NoArgsConstructor
public class Gift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly display number ("GID-{giftNo}") -- DB-generated, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "gift_no", insertable = false, updatable = false)
    private Integer giftNo;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column
    private String description;

    @Column
    private Integer moq;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Gift(String name, String category, String description, Integer moq, Integer leadTimeDays, User creator) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.moq = moq;
        this.leadTimeDays = leadTimeDays;
        this.createdBy = creator;
        this.updatedBy = creator;
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
