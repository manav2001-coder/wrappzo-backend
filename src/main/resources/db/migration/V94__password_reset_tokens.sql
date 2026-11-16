-- "Forgot password": a one-use link emailed to the person, kept as a hash only (never the link itself). Separate from the setup
-- links (user_invite_tokens), because a setup link activates an account and a reset link must never do that. Rows stay until
-- they are old, because the number a person asked for in a day is what limits them.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id, created_at);
CREATE INDEX idx_password_reset_tokens_created ON password_reset_tokens (created_at);

CREATE TABLE vendor_password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id   UUID        NOT NULL REFERENCES vendors (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_password_reset_tokens_vendor ON vendor_password_reset_tokens (vendor_id, created_at);
CREATE INDEX idx_vendor_password_reset_tokens_created ON vendor_password_reset_tokens (created_at);
