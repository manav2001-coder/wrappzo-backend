package com.wrapzzo.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** A catalogue entry in the global gift store -- either admin-authored (vendor null) or submitted by a vendor. */
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

    /** When true, an order for this gift needs a recipient photo to be produced -- enforced
     * at request time (Quick Gift) by requiring either an uploaded photo or an email to send
     * an upload link to, since there's no photo yet at that point. */
    @Column(name = "photo_required", nullable = false)
    private boolean photoRequired = false;

    /** Which commerce paths this gift can be picked from -- every gift is available in all three, unconditionally. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gift_modules", joinColumns = @JoinColumn(name = "gift_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false)
    private Set<GiftModule> modules = new HashSet<>();

    /** Base cost. Null until an admin/vendor sets one -- Total Price shows nothing until then. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Percentage, e.g. 12.50 means 12.50%, layered onto price for the Total Price shown. */
    @Column(name = "markup_fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal markupFeePercentage = BigDecimal.ZERO;

    /** Percentage, e.g. 12.50 means 12.50%, layered onto price for the Total Price shown. */
    @Column(name = "commission_fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionFeePercentage = BigDecimal.ZERO;

    /** Null for a vendor-submitted gift until (if ever) an admin actually touches it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** Null for Wrapzzo's own catalogue entries; set when a vendor submitted this gift themselves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

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

    public Gift(String name, String category, String description, Integer moq, Integer leadTimeDays, Vendor vendor) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.moq = moq;
        this.leadTimeDays = leadTimeDays;
        this.vendor = vendor;
    }

    /** price + price*markup% + price*commission% -- null until price itself is set. */
    public BigDecimal getTotalPrice() {
        if (price == null) {
            return null;
        }
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal markupAmount = price.multiply(markupFeePercentage).divide(hundred, 2, RoundingMode.HALF_UP);
        BigDecimal commissionAmount = price.multiply(commissionFeePercentage).divide(hundred, 2, RoundingMode.HALF_UP);
        return price.add(markupAmount).add(commissionAmount);
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
