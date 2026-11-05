-- When an artwork proof started waiting on its current approver, and how many reminder emails that wait has
-- had. Both reset whenever the proof moves to a new approver (a new upload, or the next level up).
ALTER TABLE order_designs
    ADD COLUMN awaiting_since TIMESTAMPTZ,
    ADD COLUMN reminders_sent INT NOT NULL DEFAULT 0,
    ADD COLUMN last_reminder_at TIMESTAMPTZ;

UPDATE order_designs SET awaiting_since = updated_at
WHERE status IN ('PENDING_REVIEW', 'PENDING_FINAL_APPROVAL');

CREATE INDEX idx_order_designs_awaiting ON order_designs (status, awaiting_since)
    WHERE status IN ('PENDING_REVIEW', 'PENDING_FINAL_APPROVAL');
