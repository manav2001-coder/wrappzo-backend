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
 * An MR's request to add a new doctor, edit an existing one's details, or remove a doctor
 * from their own list -- routed to the requester's own manager (User.reportsTo) for
 * approval before it takes effect (PRD: "these must go to the MR's senior/manager for
 * approval"). recipient is null for ADD until approved; set for EDIT/REMOVE.
 */
@Entity
@Table(name = "doctor_change_requests")
@Getter
@Setter
@NoArgsConstructor
public class DoctorChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DoctorChangeAction action;

    /** Null for ADD (doesn't exist yet); set for EDIT/REMOVE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @Column(name = "proposed_name")
    private String proposedName;

    @Column(name = "proposed_phone")
    private String proposedPhone;

    @Column(name = "proposed_email")
    private String proposedEmail;

    @Column(name = "proposed_address")
    private String proposedAddress;

    @Column(name = "proposed_dob")
    private LocalDate proposedDob;

    // The prescription-pad print details proposed for the doctor (V37) -- see Recipient.
    @Column(name = "proposed_qualification")
    private String proposedQualification;

    @Column(name = "proposed_registration_number")
    private String proposedRegistrationNumber;

    @Column(name = "proposed_speciality")
    private String proposedSpeciality;

    @Column(name = "proposed_clinic_name")
    private String proposedClinicName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DoctorChangeStatus status = DoctorChangeStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DoctorChangeRequest(Company company, User requestedBy, DoctorChangeAction action, Recipient recipient) {
        this.company = company;
        this.requestedBy = requestedBy;
        this.action = action;
        this.recipient = recipient;
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
