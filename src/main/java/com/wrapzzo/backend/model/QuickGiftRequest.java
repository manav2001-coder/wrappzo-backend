package com.wrapzzo.backend.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    // The doctor details the gift (a prescription pad) is printed with, as sent -- see Recipient for the directory copy.
    @Column(name = "recipient_qualification")
    private String recipientQualification;

    /** The doctor's pcode when ordered -- what the vendor prints on the pad. */
    @Column(name = "recipient_pcode")
    private String recipientPcode;

    @Column(name = "recipient_speciality")
    private String recipientSpeciality;

    @Column(name = "recipient_clinic_name")
    private String recipientClinicName;

    @Column(nullable = false)
    private int quantity = 1;

    /** The requester's org unit when they raised it -- routes approval and visibility through the org tree (V36). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @Column(name = "gift_message")
    private String giftMessage;

    /** Visible to the requester's own team only -- never shown to the recipient. */
    @Column(name = "internal_notes")
    private String internalNotes;

    /** The recipient's photos (V76), in the order they were added. */
    @OneToMany(mappedBy = "quickGiftRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 50)
    @OrderBy("position ASC")
    private List<QuickGiftPhoto> photos = new ArrayList<>();

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
        return !photos.isEmpty();
    }

    public void addPhoto(String key, String fileName, String contentType) {
        photos.add(new QuickGiftPhoto(this, photos.size() + 1, key, fileName, contentType));
    }

    /** Drops every photo (the recipient's own upload replaces what the AM added). The caller deletes the stored files. */
    public void clearPhotos() {
        photos.clear();
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
