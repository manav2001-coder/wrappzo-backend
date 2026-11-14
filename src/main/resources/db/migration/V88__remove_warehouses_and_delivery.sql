-- The platform's job ends when the vendor dispatches an order after the artwork is approved: no warehouses, and no "received at
-- warehouse", "out for delivery" or "delivered" stages. Production holds no such orders or warehouses; the statements below still
-- cope with any (they are folded into plain dispatched orders) so the migration is safe on every database.

-- 1. Orders and history that had gone past dispatch are simply dispatched now.
DELETE FROM order_status_history WHERE status IN ('RECEIVED_AT_WAREHOUSE', 'OUT_FOR_DELIVERY', 'DELIVERED');
UPDATE orders SET status = 'DISPATCHED' WHERE status IN ('RECEIVED_AT_WAREHOUSE', 'OUT_FOR_DELIVERY', 'DELIVERED');

ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN ('PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'CANCELLED'));
ALTER TABLE order_status_history DROP CONSTRAINT chk_order_status_history_status;
ALTER TABLE order_status_history ADD CONSTRAINT chk_order_status_history_status CHECK (status IN ('PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'CANCELLED'));

-- 2. Warehouses themselves (the orders' warehouse_id and its index go with the column).
ALTER TABLE orders DROP COLUMN warehouse_id;
DROP TABLE warehouses;

-- 3. The warehouse permissions, wherever they were granted, and the per-company switch.
DELETE FROM role_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key LIKE 'warehouse.%');
DELETE FROM designation_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key LIKE 'warehouse.%');
DELETE FROM user_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key LIKE 'warehouse.%');
DELETE FROM template_access_controls    WHERE access_control_id IN (SELECT id FROM access_controls WHERE key LIKE 'warehouse.%');
DELETE FROM company_access_controls     WHERE access_control_id IN (SELECT id FROM access_controls WHERE key LIKE 'warehouse.%');
DELETE FROM access_controls WHERE key LIKE 'warehouse.%';
DELETE FROM company_feature_flags WHERE flag_key = 'WAREHOUSE_LOGISTICS';
