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

/**
 * One decision on a proof: level 0 is the MR's review, 1..n the managers above them. Written
 * once and never changed -- the proof's approval trail. {@code onBehalfOf} is the manager a
 * level was waiting on when an order.design.final holder signed it for them.
 */
@Entity
@Table(name = "order_design_approvals")
@Getter
@NoArgsConstructor
public class OrderDesignApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private OrderDesign design;

    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderDesignDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by", nullable = false)
    private User decidedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "on_behalf_of")
    private User onBehalfOf;

    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    public OrderDesignApproval(OrderDesign design, int level, OrderDesignDecision decision, User decidedBy, User onBehalfOf,
            String comment, Instant decidedAt) {
        this.design = design;
        this.level = level;
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.onBehalfOf = onBehalfOf;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }
}
