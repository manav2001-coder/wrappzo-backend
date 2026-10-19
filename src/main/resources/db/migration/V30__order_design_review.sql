-- Gift-design (artwork) approval -- steps 5-6 of the PEL vendor process: the vendor
-- assigned to an order uploads a design proof, a company reviewer approves it or sends
-- it back with a comment for rework, looping (new version each time) until approved.
-- Deliberately order-scoped, not a full WorkOrder state machine (see Order's doc
-- comment on the fulfillment pipeline being "a later, separate pass") -- only the
-- design approve/reject/version loop is needed today.
CREATE TABLE order_designs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    version_no     INTEGER      NOT NULL,
    file_key       VARCHAR(500) NOT NULL,
    file_name      VARCHAR(255),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING_REVIEW',
    uploaded_by    UUID         NOT NULL REFERENCES vendors (id),
    reviewed_by    UUID         REFERENCES users (id),
    review_comment TEXT,
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_designs_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT uq_order_designs_order_version UNIQUE (order_id, version_no)
);

CREATE INDEX idx_order_designs_order_id ON order_designs (order_id);

-- Two new, narrow keys so a company can be scoped to exactly "review designs" and/or
-- "view order status" without also getting campaign.manage's broader create/manage
-- surface. order.track is an alternative to (not a replacement for) the existing
-- campaign.manage/campaign.submit/campaign.approve grants on OrderController -- see the
-- updated @PreAuthorize there.
INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('order.design.review', 'Review Gift Designs', 'Approve or reject vendor-uploaded artwork proofs for an order, requesting rework when rejected', 'Order', 'FUNCTIONAL'),
    ('order.track', 'Track Orders', 'View order status and fulfillment timeline, read-only, without campaign creation/management access', 'Order', 'FUNCTIONAL');
