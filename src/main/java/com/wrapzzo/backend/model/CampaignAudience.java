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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One audience rule for a Campaign -- multiple rows combine with OR (a user is in the
 * audience if any rule matches them). orgUnit is set only for ORG_UNIT (resolved via
 * OrgUnit.ancestorIds); anchorUser is set only for MANAGER_TEAM (resolved via
 * User.reportsTo, recursively); user is set only for SELECTED_USERS (one row per
 * included user). ALL_COMPANY_USERS sets neither.
 */
@Entity
@Table(name = "campaign_audiences")
@Getter
@Setter
@NoArgsConstructor
public class CampaignAudience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignAudienceType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_user_id")
    private User anchorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CampaignAudience(Campaign campaign, CampaignAudienceType type, OrgUnit orgUnit, User anchorUser, User user) {
        this.campaign = campaign;
        this.type = type;
        this.orgUnit = orgUnit;
        this.anchorUser = anchorUser;
        this.user = user;
        this.createdAt = Instant.now();
    }
}
