-- 1. Warehouses (used when a company turns on WAREHOUSE_LOGISTICS): the vendor ships an order to
--    the company's warehouse, which receives it and delivers it onward. A warehouse serves one
--    org unit and everything under it, or the whole company (org_unit_id null).
CREATE TABLE warehouses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    address     TEXT         NOT NULL,
    contact_phone VARCHAR(50),
    org_unit_id UUID         REFERENCES org_units (id) ON DELETE SET NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_warehouses_company_id ON warehouses (company_id);

-- An order routed through a warehouse; null = the vendor ships straight to the recipient.
ALTER TABLE orders ADD COLUMN warehouse_id UUID REFERENCES warehouses (id) ON DELETE SET NULL;
CREATE INDEX idx_orders_warehouse_id ON orders (warehouse_id);

-- Two more stages for a warehouse-routed order: received at the warehouse, then out for delivery.
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN (
    'PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'RECEIVED_AT_WAREHOUSE', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'));
ALTER TABLE order_status_history DROP CONSTRAINT chk_order_status_history_status;
ALTER TABLE order_status_history ADD CONSTRAINT chk_order_status_history_status CHECK (status IN (
    'PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'RECEIVED_AT_WAREHOUSE', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'));

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('warehouse.manage', 'Run Warehouses', 'Set up the company''s warehouses and receive and deliver the orders routed through them', 'Order', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name = 'Company Admin'
  AND ac.key = 'warehouse.manage'
ON CONFLICT DO NOTHING;

-- 2. Recurring orders: a Quick Gift placed again every month (pads for a doctor, say). Each run
--    goes through the same path as a manual request -- approval or straight to the vendor.
CREATE TABLE recurring_order_plans (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                    UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    requested_by                  UUID         NOT NULL REFERENCES users (id),
    gift_id                       UUID         NOT NULL REFERENCES gifts (id),
    recipient_id                  UUID         REFERENCES recipients (id) ON DELETE SET NULL,
    recipient_name                VARCHAR(255) NOT NULL,
    recipient_phone               VARCHAR(50)  NOT NULL,
    recipient_email               VARCHAR(255),
    delivery_address              TEXT         NOT NULL,
    recipient_qualification       VARCHAR(255),
    recipient_registration_number VARCHAR(100),
    recipient_speciality          VARCHAR(255),
    recipient_clinic_name         VARCHAR(255),
    gift_message                  TEXT,
    quantity                      INTEGER      NOT NULL,
    next_run_on                   DATE         NOT NULL,
    runs_completed                INTEGER      NOT NULL DEFAULT 0,
    status                        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_error                    TEXT,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_recurring_order_plans_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    CONSTRAINT chk_recurring_order_plans_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_recurring_order_plans_due ON recurring_order_plans (status, next_run_on);
CREATE INDEX idx_recurring_order_plans_requested_by ON recurring_order_plans (requested_by);
