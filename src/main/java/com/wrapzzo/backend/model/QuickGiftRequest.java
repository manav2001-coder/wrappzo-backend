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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Quick Gift (PRD Part 2, quick path) -- a single-recipient gift request with no campaign,
 * but still routed through the same Company Admin approval gate as a Campaign submission:
 * SUBMITTED -> APPROVED (an Order is created) or REJECTED. order is null until approved.
 */
@Entity
@Table(name = "quick_gift_requests")
@Getter
@Setter
@NoArgsConstructor
public class QuickGiftRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null until approved. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Human-friendly display number ("QG-{requestNo}") -- DB-generated, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "request_no", insertable = false, updatable = false)
    private Integer requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_id", nullable = false)
    private Gift gift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "recipient_dob")
    private LocalDate recipientDob;

    @Column(name = "gift_message")
    private String giftMessage;

    /** Visible to the requester's own team only -- never shown to the recipient. */
    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "photo_file_name")
    private String photoFileName;

    @Column(name = "photo_content_type")
    private String photoContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuickGiftStatus status = QuickGiftStatus.SUBMITTED;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public QuickGiftRequest(
            Company company,
            Gift gift,
            User requestedBy,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            String deliveryAddress,
            String giftMessage,
            String internalNotes) {
        this.company = company;
        this.gift = gift;
        this.requestedBy = requestedBy;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientEmail = recipientEmail;
        this.deliveryAddress = deliveryAddress;
        this.giftMessage = giftMessage;
        this.internalNotes = internalNotes;
    }

    public boolean hasPhoto() {
        return photoKey != null;
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
