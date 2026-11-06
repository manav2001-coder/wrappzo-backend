-- Order approval: a company can require its requester's manager to approve a Quick Gift / PEL order before it goes to the vendor.
-- The company admin switches it on and picks the approver as a role plus where to find them (the same two scopes as an
-- artwork approval step: the nearest holder above the requester, or anyone holding it in the requester's division).
ALTER TABLE companies
    ADD COLUMN order_approval_enabled BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN order_approval_role_id UUID        REFERENCES roles (id) ON DELETE SET NULL,
    ADD COLUMN order_approval_scope   VARCHAR(20) NOT NULL DEFAULT 'REPORTING_LINE'
        CONSTRAINT chk_companies_order_approval_scope CHECK (order_approval_scope IN ('REPORTING_LINE', 'DIVISION'));

-- Where an order stands on that approval. NOT_REQUIRED for every order raised without it (all existing ones).
ALTER TABLE orders
    ADD COLUMN approval_status        VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED'
        CONSTRAINT chk_orders_approval_status CHECK (approval_status IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED')),
    ADD COLUMN approval_decided_by_id UUID        REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN approval_decided_at    TIMESTAMPTZ,
    ADD COLUMN approval_comment       TEXT;

CREATE INDEX idx_orders_approval_pending ON orders (company_id) WHERE approval_status = 'PENDING';

-- Who may see and change the setup. Only the access_controls rows are inserted here: CompanyFeatureRegistrar registers them
-- for every company and CompanyAdminAccessReconciler tops up the Company Admin templates at startup. Deciding on an order
-- is approval.decide, the same key as deciding on artwork in the approval cycle.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('order_approval.view', 'order_approval', 'view', 'Approvals', 'Order approval setup', 'See whether orders need approval and who approves them', 'FUNCTIONAL', true, 611),
    ('order_approval.configure', 'order_approval', 'configure', 'Approvals', 'Set order approval', 'Switch order approval on or off and choose who approves orders', 'FUNCTIONAL', true, 612);
