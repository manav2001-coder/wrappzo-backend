-- Quick Gift (PRD Part 2, quick path) -- a single-recipient gift request with no
-- campaign, but still routed through the same Company Admin approval gate as a
-- Campaign submission: SUBMITTED -> APPROVED (becomes an Order) or REJECTED.
-- OrderSource.QUICK_GIFT was reserved for exactly this from the start ("all three
-- share the same Order engine") -- campaign_id/submission_id are only ever
-- populated for CAMPAIGN orders now.
ALTER TABLE orders ALTER COLUMN campaign_id DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN submission_id DROP NOT NULL;
ALTER TABLE orders ADD CONSTRAINT chk_orders_source_linkage CHECK (
    (source = 'CAMPAIGN' AND campaign_id IS NOT NULL AND submission_id IS NOT NULL)
    OR (source <> 'CAMPAIGN' AND campaign_id IS NULL AND submission_id IS NULL)
);

-- The Quick Gift-specific request envelope -- recipient/delivery/personalization/
-- internal notes, plus its own approval workflow (mirrors submissions: SUBMITTED,
-- APPROVED, REJECTED). order_id is only set once a Company Admin approves it.
CREATE TABLE quick_gift_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID         UNIQUE REFERENCES orders (id),
    company_id         UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    request_no         INTEGER      GENERATED ALWAYS AS IDENTITY,
    gift_id            UUID         NOT NULL REFERENCES gifts (id),
    requested_by       UUID         NOT NULL REFERENCES users (id),
    recipient_name     VARCHAR(255) NOT NULL,
    recipient_phone    VARCHAR(50)  NOT NULL,
    recipient_email    VARCHAR(255),
    delivery_address   TEXT         NOT NULL,
    gift_message       TEXT,
    internal_notes     TEXT,
    photo_key          VARCHAR(500),
    photo_file_name    VARCHAR(255),
    photo_content_type VARCHAR(100),
    status             VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',
    rejection_reason   TEXT,
    reviewed_by        UUID         REFERENCES users (id),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_quick_gift_requests_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_quick_gift_requests_company_id ON quick_gift_requests (company_id);
CREATE INDEX idx_quick_gift_requests_requested_by ON quick_gift_requests (requested_by);
CREATE INDEX idx_quick_gift_requests_company_id_status ON quick_gift_requests (company_id, status);

-- Same shape as recipient_upload_tokens (V21) -- a single-use, expiring link handed to a
-- Quick Gift recipient so they can upload their own photo, once their gift is approved.
CREATE TABLE quick_gift_upload_tokens (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quick_gift_request_id UUID        NOT NULL REFERENCES quick_gift_requests (id) ON DELETE CASCADE,
    token_hash            VARCHAR(64) NOT NULL UNIQUE,
    expires_at            TIMESTAMPTZ NOT NULL,
    used_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quick_gift_upload_tokens_quick_gift_request_id ON quick_gift_upload_tokens (quick_gift_request_id);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('quickgift.create', 'Request Quick Gifts', 'Send a single personalized gift request immediately, from the Gift Store, no campaign required', 'Quick Gift', 'FUNCTIONAL'),
    ('dashboard.view', 'View Dashboard', 'See a home-screen overview of campaigns, orders, and Quick Gift activity', 'Dashboard', 'FUNCTIONAL');

-- Every company role (Company Admin and Member alike) can send a Quick Gift, and gets a
-- Dashboard -- company-wide for Company Admin, scoped to their own activity for Member
-- (see DashboardService).
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND ac.key IN ('quickgift.create', 'dashboard.view')
  AND NOT EXISTS (
      SELECT 1 FROM role_access_controls rac
      WHERE rac.role_id = r.id AND rac.access_control_id = ac.id
  );
