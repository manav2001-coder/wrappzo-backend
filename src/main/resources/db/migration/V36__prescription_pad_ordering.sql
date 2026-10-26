-- Prescription-pad ordering (Alembic): an MR picks or adds a doctor, chooses a vendor's pad
-- product and a quantity, and the order goes straight to that vendor -- no approval -- who
-- then loops artwork with the MR until it is approved, and only then may dispatch.
-- Everything here is additive and defaults to today's behaviour for every other company.

-- 1. Doctor details a pad is printed with. On the shared directory (reused on every reorder)
--    and snapshotted onto the request (what was actually sent to print).
ALTER TABLE recipients ADD COLUMN qualification       VARCHAR(255);
ALTER TABLE recipients ADD COLUMN registration_number VARCHAR(100);
ALTER TABLE recipients ADD COLUMN speciality          VARCHAR(255);
ALTER TABLE recipients ADD COLUMN clinic_name         VARCHAR(255);

-- A doctor's medical-council registration number is the one natural key a directory of
-- doctors has: two MRs adding "Dr. A. Shah" with different phone numbers are the same
-- doctor if this matches.
CREATE UNIQUE INDEX uq_recipients_company_registration_number
    ON recipients (company_id, lower(registration_number))
    WHERE registration_number IS NOT NULL;

ALTER TABLE quick_gift_requests ADD COLUMN quantity                      INTEGER NOT NULL DEFAULT 1;
ALTER TABLE quick_gift_requests ADD COLUMN recipient_qualification       VARCHAR(255);
ALTER TABLE quick_gift_requests ADD COLUMN recipient_registration_number VARCHAR(100);
ALTER TABLE quick_gift_requests ADD COLUMN recipient_speciality          VARCHAR(255);
ALTER TABLE quick_gift_requests ADD COLUMN recipient_clinic_name         VARCHAR(255);
ALTER TABLE quick_gift_requests ADD CONSTRAINT chk_quick_gift_requests_quantity CHECK (quantity > 0);

ALTER TABLE orders ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1;
ALTER TABLE orders ADD CONSTRAINT chk_orders_quantity CHECK (quantity > 0);

-- 2. A gift whose order cannot leave the vendor until its artwork is approved.
ALTER TABLE gifts ADD COLUMN artwork_required BOOLEAN NOT NULL DEFAULT false;

-- 3. The org unit an order / request belongs to, fixed when it is raised (the requester's
--    unit at that moment), so visibility and approvals follow the org tree and do not move
--    when the MR is later transferred.
ALTER TABLE orders ADD COLUMN org_unit_id UUID REFERENCES org_units (id) ON DELETE SET NULL;
ALTER TABLE quick_gift_requests ADD COLUMN org_unit_id UUID REFERENCES org_units (id) ON DELETE SET NULL;
CREATE INDEX idx_orders_org_unit_id ON orders (org_unit_id);
CREATE INDEX idx_quick_gift_requests_org_unit_id ON quick_gift_requests (org_unit_id);

UPDATE quick_gift_requests q
SET org_unit_id = u.org_unit_id
FROM users u
WHERE u.id = q.requested_by;

UPDATE orders o
SET org_unit_id = q.org_unit_id
FROM quick_gift_requests q
WHERE q.order_id = o.id;

UPDATE orders o
SET org_unit_id = u.org_unit_id
FROM submissions s
JOIN users u ON u.id = s.submitted_by
WHERE s.id = o.submission_id;

-- 4. Who sees beyond their own org subtree. Company Admin keeps today's company-wide view.
INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('org.scope.all', 'See All Org Units', 'See orders, requests and approvals from every org unit in the company, not just your own unit and the units under it', 'Organisation', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name = 'Company Admin'
  AND ac.key = 'org.scope.all'
ON CONFLICT DO NOTHING;

-- 5. The MR who placed an order reviews its artwork by default; any other role can be given
--    order.design.review too, and reviews within its own org subtree.
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name IN ('Company Admin', 'Member')
  AND ac.key = 'order.design.review'
ON CONFLICT DO NOTHING;
