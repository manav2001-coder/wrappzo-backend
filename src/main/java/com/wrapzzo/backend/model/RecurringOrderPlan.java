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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Quick Gift placed again every month (a doctor's prescription pads, say). The first order is
 * placed when the plan is made; RecurringOrderJob places each next one on {@code nextRunOn},
 * through the same path as a manual request. The recipient fields are a snapshot of the first
 * order, used when the directory entry ({@code recipient}) is gone.
 */
@Entity
@Table(name = "recurring_order_plans")
@Getter
@Setter
@NoArgsConstructor
public class RecurringOrderPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_id", nullable = false)
    private Gift gift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "recipient_qualification")
    private String recipientQualification;

    @Column(name = "recipient_registration_number")
    private String recipientRegistrationNumber;

    @Column(name = "recipient_speciality")
    private String recipientSpeciality;

    @Column(name = "recipient_clinic_name")
    private String recipientClinicName;

    @Column(name = "gift_message")
    private String giftMessage;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "next_run_on", nullable = false)
    private LocalDate nextRunOn;

    /** Orders this plan has placed, the first one included. */
    @Column(name = "runs_completed", nullable = false)
    private int runsCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecurringPlanStatus status = RecurringPlanStatus.ACTIVE;

    /** Why the last scheduled run failed -- cleared by the next one that succeeds. */
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
