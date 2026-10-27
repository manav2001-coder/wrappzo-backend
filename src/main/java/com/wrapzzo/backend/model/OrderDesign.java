package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One version of an order's artwork proof (PEL process steps 5-6: Design &amp; QC, then
 * Review &amp; rework). The vendor assigned to the order uploads a version; a company
 * reviewer (order.design.review) approves it or rejects it with a comment, at which point
 * the vendor uploads the next version. versionNo is 1-based and increments per order.
 */
@Entity
@Table(name = "order_designs")
@Getter
@Setter
@NoArgsConstructor
public class OrderDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderDesignStatus status = OrderDesignStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private Vendor uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    /** The MR's approval, when a senior's final approval followed it (MARKETING_DESIGN_APPROVAL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_approved_by")
    private User firstApprovedBy;

    @Column(name = "first_approved_at")
    private Instant firstApprovedAt;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderDesign(Order order, int versionNo, String fileKey, String fileName, Vendor uploadedBy) {
        this.order = order;
        this.versionNo = versionNo;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.uploadedBy = uploadedBy;
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
