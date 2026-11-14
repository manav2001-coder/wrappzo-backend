-- Indexes found by load-testing a 6,000-person company (190k doctors, 110k orders). Each one serves a query that was scanning the whole table.

-- The duplicate-doctor check (RecipientRepository.findMatch) matches on lower(trim(name)) + trim(phone); a bulk upload runs it once per row,
-- and without an index it read every doctor of the company each time (38 ms at 190k doctors, 0.05 ms with this).
CREATE INDEX IF NOT EXISTS idx_recipients_dedupe ON recipients (company_id, lower(btrim(name)), btrim(phone));

-- The dashboard's per-person totals (requested, dispatched, covered) and "when was it dispatched" lookups.
CREATE INDEX IF NOT EXISTS idx_orders_owner_status_created ON orders (owner_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_status_history_order_status_at ON order_status_history (order_id, status, changed_at DESC);
