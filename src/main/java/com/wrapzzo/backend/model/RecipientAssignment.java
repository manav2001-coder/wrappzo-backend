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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One doctor on one MR's own working list -- separate from the shared company-wide recipients directory. Deleting this row never deletes the Recipient itself. */
@Entity
@Table(name = "recipient_assignments")
@Getter
@Setter
@NoArgsConstructor
public class RecipientAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mr_user_id", nullable = false)
    private User mrUser;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    public RecipientAssignment(Recipient recipient, User mrUser) {
        this.recipient = recipient;
        this.mrUser = mrUser;
    }

    @PrePersist
    void onCreate() {
        assignedAt = Instant.now();
    }
}
