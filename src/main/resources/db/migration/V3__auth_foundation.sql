-- Auth foundation: brute-force lockout bookkeeping on users, plus a table for
-- revocable/rotatable refresh tokens. Access tokens are stateless JWTs and
-- are never stored — only refresh tokens need a database record, since
-- they're the thing we can actually revoke.

ALTER TABLE users
    ADD COLUMN failed_login_attempts SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until           TIMESTAMPTZ;

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 hex digest of the actual refresh token value. The raw value is
    -- only ever handed to the client, never stored — this is a lookup key,
    -- not a password, so a fast hash is appropriate here (unlike password_hash).
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
