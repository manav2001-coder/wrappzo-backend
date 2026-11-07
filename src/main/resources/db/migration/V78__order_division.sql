-- The division (top of the org tree) an order was raised in, kept on the order so the vendor portal can filter and count by it in the
-- database. It follows the same rule the portal always used: the order's own org unit, else its owner's, and that unit's top ancestor.
ALTER TABLE orders ADD COLUMN division_id UUID REFERENCES org_units (id) ON DELETE SET NULL;

UPDATE orders o
SET division_id = COALESCE(ou.ancestor_ids[1], ou.id)
FROM org_units ou
WHERE ou.id = COALESCE(o.org_unit_id, (SELECT u.org_unit_id FROM users u WHERE u.id = o.owner_id));

CREATE INDEX idx_orders_vendor_division ON orders (assigned_vendor_id, division_id);
