-- Vendors are deliberately NOT users -- separate identity/auth track entirely
-- (own password, own lockout bookkeeping, own refresh tokens), per the PRD's
-- requirement that vendor auth stay architecturally independent from
-- Company/Platform auth. Every vendor's capability set is fixed and
-- identical (see AccessControl catalog comment) -- there is no per-vendor
-- Role, so no role_id here.
CREATE TABLE vendors (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    contact_email         VARCHAR(320) NOT NULL UNIQUE,
    contact_phone         VARCHAR(32),
    password_hash         VARCHAR(255) NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts SMALLINT     NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_by            UUID         NOT NULL REFERENCES users (id),
    updated_by            UUID         NOT NULL REFERENCES users (id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendors_status ON vendors (status);

-- Same shape as refresh_tokens, deliberately a separate table -- a vendor
-- session must never share infrastructure with a User session.
CREATE TABLE vendor_refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id  UUID         NOT NULL REFERENCES vendors (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendor_refresh_tokens_vendor_id ON vendor_refresh_tokens (vendor_id);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('vendor.manage', 'Manage Vendors', 'Onboard, edit, suspend, and reactivate vendors', 'Vendor', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT roles.id, access_controls.id
FROM roles, access_controls
WHERE roles.name = 'Super Admin' AND roles.company_id IS NULL
  AND access_controls.key = 'vendor.manage';
