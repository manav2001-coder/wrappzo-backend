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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step of a company's artwork approval chain after the AM's own review (V60). Steps are numbered from 1, in order. */
@Entity
@Table(name = "company_artwork_approval_steps")
@Getter
@Setter
@NoArgsConstructor
public class CompanyArtworkApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArtworkApprovalScope scope;

    public CompanyArtworkApprovalStep(Company company, int stepNo, Role role, ArtworkApprovalScope scope) {
        this.company = company;
        this.stepNo = stepNo;
        this.role = role;
        this.scope = scope;
    }
}
