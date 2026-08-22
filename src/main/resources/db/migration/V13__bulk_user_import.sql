-- Bulk employee import: adds the columns a client's roster sheet typically
-- carries beyond name/email (all optional -- not every client's sheet has
-- all of these), plus a UserInviteToken table mirroring VendorInviteToken so
-- bulk-imported employees get the same "set your own password via emailed
-- link" flow vendors already have. reports_to already existed on users.

ALTER TABLE users ADD COLUMN mobile VARCHAR(30);
ALTER TABLE users ADD COLUMN designation VARCHAR(150);
ALTER TABLE users ADD COLUMN employee_code VARCHAR(50);

-- A client's own employee code, unique within their company but not globally
-- (two different companies may reuse the same code scheme).
CREATE UNIQUE INDEX idx_users_company_employee_code ON users (company_id, employee_code)
    WHERE employee_code IS NOT NULL;

CREATE TABLE user_invite_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_invite_tokens_user_id ON user_invite_tokens (user_id);
