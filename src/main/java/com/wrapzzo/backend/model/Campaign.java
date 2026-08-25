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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A company's coordinated gifting initiative (PRD Part 2.5). Not a SaaS product of its
 * own -- an approved Submission against this Campaign becomes an ordinary Order, sharing
 * the same downstream engine as Quick Gift and Bulk Gifting.
 */
@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Human-friendly display number ("CMP-{campaignNo}") -- DB-generated, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "campaign_no", insertable = false, updatable = false)
    private Integer campaignNo;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_category", nullable = false, length = 32)
    private CampaignRecipientCategory recipientCategory;

    /** The gift(s) this campaign offers -- mandatory (at least one), enforced in CampaignService, not the DB.
     * A recipient may be given any one of these; which one is captured per-Submission, not assumed campaign-wide. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "campaign_gifts",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "gift_id"))
    private Set<Gift> gifts = new HashSet<>();

    @Column
    private String instructions;

    @Column(name = "submission_deadline")
    private Instant submissionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CampaignStatus status = CampaignStatus.DRAFT;

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

    public Campaign(
            Company company,
            String name,
            CampaignPurpose purpose,
            CampaignRecipientCategory recipientCategory,
            Set<Gift> gifts,
            String instructions,
            Instant submissionDeadline,
            User creator) {
        this.company = company;
        this.name = name;
        this.purpose = purpose;
        this.recipientCategory = recipientCategory;
        this.gifts = gifts;
        this.instructions = instructions;
        this.submissionDeadline = submissionDeadline;
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
