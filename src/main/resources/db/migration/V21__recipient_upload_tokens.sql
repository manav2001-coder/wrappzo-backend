-- Same shape as vendor_invite_tokens/user_invite_tokens: a single-use, expiring, opaque
-- link handed to an unauthenticated recipient (via email) so they can upload their own
-- photo against a Submission that was approved without one.
CREATE TABLE recipient_upload_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID        NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recipient_upload_tokens_submission_id ON recipient_upload_tokens (submission_id);
