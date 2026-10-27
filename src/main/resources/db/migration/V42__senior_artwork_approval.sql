-- Senior artwork approval (company flag MARKETING_DESIGN_APPROVAL): once the MR approves a proof
-- it waits for the MR's manager (users.reports_to_id), or an order.design.final holder, before
-- the vendor may print. The MR's approval is kept in its own columns; reviewed_by/reviewed_at
-- stay the decision that settled the proof (the senior's approval, or whoever rejected it).
ALTER TABLE order_designs ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE order_designs DROP CONSTRAINT chk_order_designs_status;
ALTER TABLE order_designs ADD CONSTRAINT chk_order_designs_status
    CHECK (status IN ('PENDING_REVIEW', 'PENDING_FINAL_APPROVAL', 'APPROVED', 'REJECTED'));

ALTER TABLE order_designs ADD COLUMN first_approved_by UUID REFERENCES users (id);
ALTER TABLE order_designs ADD COLUMN first_approved_at TIMESTAMPTZ;

CREATE INDEX idx_order_designs_status ON order_designs (status);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('order.design.final', 'Final Artwork Approval', 'Give the final approval on artwork an MR has approved, for any MR in the company, not just your direct reports', 'Order', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name = 'Company Admin'
  AND ac.key = 'order.design.final'
ON CONFLICT DO NOTHING;
