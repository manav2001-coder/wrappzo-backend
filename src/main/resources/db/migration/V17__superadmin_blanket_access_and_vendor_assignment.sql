-- Super Admin should never need a per-feature access_controls grant added by hand every
-- time a new module ships (campaign.manage, order.manage, ...). A dedicated flag on the
-- role, checked in AccessControlCacheService, gives it every FUNCTIONAL key dynamically
-- instead of maintaining an ever-growing explicit grant list.
ALTER TABLE roles ADD COLUMN is_platform_super_admin BOOLEAN NOT NULL DEFAULT false;
UPDATE roles SET is_platform_super_admin = true WHERE company_id IS NULL AND name = 'Super Admin';

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('order.manage', 'Manage Orders', 'View orders across every company and assign them to a fulfillment vendor', 'Order', 'FUNCTIONAL');

-- Vendor fulfillment assignment (PRD Part 1.5 -- Fulfillment & Operations, inside Admin).
-- Nullable: an order starts unassigned (PROCESSING) until Admin assigns it, either
-- automatically or by picking a specific vendor.
ALTER TABLE orders ADD COLUMN assigned_vendor_id UUID REFERENCES vendors (id);
ALTER TABLE orders ADD COLUMN assignment_mode VARCHAR(16);
ALTER TABLE orders ADD COLUMN assigned_at TIMESTAMPTZ;
ALTER TABLE orders ADD CONSTRAINT chk_orders_assignment_mode CHECK (assignment_mode IN ('AUTO', 'MANUAL'));

CREATE INDEX idx_orders_assigned_vendor_id ON orders (assigned_vendor_id);
