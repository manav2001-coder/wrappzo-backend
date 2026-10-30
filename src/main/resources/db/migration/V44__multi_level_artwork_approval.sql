-- Multi-level artwork approval (company flag MARKETING_DESIGN_APPROVAL): after the MR approves a
-- proof it climbs the MR's reporting line (users.reports_to_id) one manager per level, up to the
-- company's artwork_approval_levels. 1 keeps the V42 behaviour (the MR's manager signs off).
ALTER TABLE companies ADD COLUMN artwork_approval_levels INTEGER NOT NULL DEFAULT 1
    CONSTRAINT chk_companies_artwork_approval_levels CHECK (artwork_approval_levels BETWEEN 1 AND 5);

-- While PENDING_FINAL_APPROVAL: the level the proof waits on, and how many levels its chain has.
ALTER TABLE order_designs ADD COLUMN approval_level INTEGER;
ALTER TABLE order_designs ADD COLUMN approval_levels INTEGER;

-- Every decision on a proof, in order: level 0 is the MR's review, 1..n the managers above.
-- on_behalf_of is set when an order.design.final holder signed a level for the manager it was waiting on.
CREATE TABLE order_design_approvals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    design_id    UUID        NOT NULL REFERENCES order_designs (id) ON DELETE CASCADE,
    level        INTEGER     NOT NULL,
    decision     VARCHAR(16) NOT NULL CONSTRAINT chk_order_design_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    decided_by   UUID        NOT NULL REFERENCES users (id),
    on_behalf_of UUID        REFERENCES users (id),
    comment      TEXT,
    decided_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_order_design_approvals_design ON order_design_approvals (design_id);

-- Proofs already waiting on a senior (V42) wait on level 1 of 1.
UPDATE order_designs SET approval_level = 1, approval_levels = 1 WHERE status = 'PENDING_FINAL_APPROVAL';

-- Carry the existing decisions into the trail: the MR's approval, then whoever settled the proof.
INSERT INTO order_design_approvals (design_id, level, decision, decided_by, decided_at)
SELECT id, 0, 'APPROVED', first_approved_by, first_approved_at
FROM order_designs
WHERE first_approved_by IS NOT NULL AND first_approved_at IS NOT NULL;

INSERT INTO order_design_approvals (design_id, level, decision, decided_by, comment, decided_at)
SELECT id, CASE WHEN first_approved_by IS NULL THEN 0 ELSE 1 END, status, reviewed_by, review_comment, reviewed_at
FROM order_designs
WHERE status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL;
