package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One company's own name for one navigation menu. No row for a menu means its default name. */
@Entity
@Table(name = "company_menu_labels")
@Getter
@Setter
@NoArgsConstructor
public class CompanyMenuLabel {

    @EmbeddedId
    private Key id;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanyMenuLabel(UUID companyId, String menuId, String label) {
        this.id = new Key(companyId, menuId);
        this.label = label;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "company_id", nullable = false)
        private UUID companyId;

        @Column(name = "menu_id", nullable = false, length = 64)
        private String menuId;
    }
}
