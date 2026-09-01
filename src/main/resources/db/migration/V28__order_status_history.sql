-- Per-stage timeline for an order's fulfillment journey (Processing -> Production Queue ->
-- Dispatched -> Delivered, or Cancelled at any point). Previously only the *current* status
-- was tracked (orders.status + orders.updated_at, overwritten on every change, including
-- changes unrelated to status) -- there was no way to show exactly when an order hit each
-- stage. Every status change from now on writes a new row here instead of just overwriting
-- the order row.
CREATE TABLE order_status_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status     VARCHAR(24) NOT NULL,
    changed_by UUID        REFERENCES users (id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_status_history_status CHECK (status IN ('PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);

-- Backfill for orders that already existed before this table did. Every order started at
-- PROCESSING when created -- that much we know for certain from created_at. If it has since
-- moved to a different stage, add a second row for its current stage at updated_at (the
-- best signal available; any stages it passed through in between aren't recoverable). Every
-- transition from this point forward is captured exactly, one row each.
INSERT INTO order_status_history (order_id, status, changed_by, changed_at)
SELECT id, 'PROCESSING', created_by, created_at FROM orders;

INSERT INTO order_status_history (order_id, status, changed_by, changed_at)
SELECT id, status, created_by, updated_at FROM orders WHERE status <> 'PROCESSING';
