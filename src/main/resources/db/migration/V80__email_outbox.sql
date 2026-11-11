-- Every email the app sends is written here first, in the same transaction as the change that caused it, and a
-- dispatcher sends it afterwards. So a restart or a Resend outage no longer loses emails: they are retried.
CREATE TABLE email_outbox (
    id              UUID PRIMARY KEY,
    to_address      VARCHAR(320) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    html            TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,           -- PENDING, SENDING, SENT, FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    claimed_until   TIMESTAMPTZ,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ
);

-- The dispatcher's question: what is due, oldest first?
CREATE INDEX idx_email_outbox_due ON email_outbox (next_attempt_at) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX idx_email_outbox_finished ON email_outbox (status, created_at) WHERE status IN ('SENT', 'FAILED');
