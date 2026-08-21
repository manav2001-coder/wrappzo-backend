-- Single-use, expiring token a vendor uses to set their own password after
-- being onboarded, replacing the old "Super Admin hands them a temporary
-- password" flow. Same hashing approach as refresh_tokens -- the raw token
-- is only ever emailed to the vendor, never stored.
CREATE TABLE vendor_invite_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id  UUID        NOT NULL REFERENCES vendors (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendor_invite_tokens_vendor_id ON vendor_invite_tokens (vendor_id);
