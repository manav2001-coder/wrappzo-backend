-- The doctor's pcode on the order itself, next to the name it identifies, so every screen can show "(PCODE) Name" and every
-- list can search and filter by it without joining back to the request. Existing orders take it from their request.
ALTER TABLE orders ADD COLUMN recipient_pcode VARCHAR(255);

UPDATE orders o
   SET recipient_pcode = q.recipient_pcode
  FROM quick_gift_requests q
 WHERE q.order_id = o.id AND q.recipient_pcode IS NOT NULL;

CREATE INDEX idx_orders_company_id_recipient_pcode ON orders (company_id, lower(recipient_pcode));
