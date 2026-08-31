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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The commercial transaction (PRD 2.10) -- created exactly once per approved Submission
 * (see the unique constraint on submission_id). Deliberately holds only the customer-
 * facing shape; the fulfillment/artwork/QC/vendor-dispatch pipeline (WorkOrder) is a
 * later, separate pass.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Human-friendly display number ("ORD-{orderNo}") -- DB-generated, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "order_no", insertable = false, updatable = false)
    private Integer orderNo;

    /** Null for a Quick Gift order (source != CAMPAIGN) -- see chk_orders_source_linkage. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** Null for a Quick Gift order (source != CAMPAIGN) -- see chk_orders_source_linkage. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", unique = true)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_id")
    private Gift gift;

    /** Snapshotted off the submission's answers at approval time -- stable even if fields are later edited. */
    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    /** Snapshotted off the gift's total price (price + fee percentages) at approval time --
     * stable even if the gift is repriced afterward. Null for an order whose gift had no price set. */
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderSource source = OrderSource.CAMPAIGN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PROCESSING;

    /** Fulfillment assignment (PRD Part 1.5) -- Admin-only, set once Super Admin routes this order to a vendor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_vendor_id")
    private Vendor assignedVendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", length = 16)
    private OrderAssignmentMode assignmentMode;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Order(
            Company company,
            Campaign campaign,
            Submission submission,
            Gift gift,
            String recipientName,
            String recipientPhone,
            User createdBy) {
        this.company = company;
        this.campaign = campaign;
        this.submission = submission;
        this.gift = gift;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.totalPrice = gift != null ? gift.getTotalPrice() : null;
        this.createdBy = createdBy;
    }

    /** Quick Gift path -- no campaign/submission; created the moment the request is sent, no approval gate. */
    public Order(Company company, Gift gift, OrderSource source, String recipientName, String recipientPhone, User createdBy) {
        this.company = company;
        this.gift = gift;
        this.source = source;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.totalPrice = gift != null ? gift.getTotalPrice() : null;
        this.createdBy = createdBy;
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
