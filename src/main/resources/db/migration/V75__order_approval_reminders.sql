-- Daily reminders to the approver of an order that waits on them (at most 3, a day apart), counted per order.
ALTER TABLE orders
    ADD COLUMN approval_reminders_sent   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN approval_last_reminder_at TIMESTAMPTZ;
