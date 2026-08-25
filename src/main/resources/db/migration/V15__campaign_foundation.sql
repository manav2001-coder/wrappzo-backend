-- Campaigns (PRD Part 2.5) -- a company's coordinated gifting initiative. Not a SaaS
-- product of its own: an approved Submission against a Campaign becomes an ordinary
-- Order (see V16), sharing the same downstream engine as every other commerce path.
CREATE TABLE campaigns (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id           UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    campaign_no          INTEGER      GENERATED ALWAYS AS IDENTITY,
    name                 VARCHAR(255) NOT NULL,
    purpose              VARCHAR(32)  NOT NULL,
    recipient_category   VARCHAR(32)  NOT NULL,
    gift_id              UUID         REFERENCES gifts (id),
    instructions         TEXT,
    submission_deadline  TIMESTAMPTZ,
    status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_by           UUID         NOT NULL REFERENCES users (id),
    updated_by           UUID         NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_campaigns_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_campaigns_company_id ON campaigns (company_id);
CREATE INDEX idx_campaigns_company_id_status ON campaigns (company_id, status);
CREATE INDEX idx_campaigns_gift_id ON campaigns (gift_id);

-- Who may submit into a Campaign. Multiple rows per campaign combine with OR. org_unit_id
-- is set only for type = ORG_UNIT (resolved via org_units.ancestor_ids), anchor_user_id
-- only for MANAGER_TEAM (resolved by walking users.reports_to_id), user_id only for
-- SELECTED_USERS. ALL_COMPANY_USERS sets neither.
CREATE TABLE campaign_audiences (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id    UUID        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    type           VARCHAR(32) NOT NULL,
    org_unit_id    UUID        REFERENCES org_units (id) ON DELETE CASCADE,
    anchor_user_id UUID        REFERENCES users (id) ON DELETE CASCADE,
    user_id        UUID        REFERENCES users (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_campaign_audiences_type
        CHECK (type IN ('ALL_COMPANY_USERS', 'ORG_UNIT', 'MANAGER_TEAM', 'SELECTED_USERS'))
);

CREATE INDEX idx_campaign_audiences_campaign_id ON campaign_audiences (campaign_id);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('campaign.manage', 'Manage Campaigns', 'Create, edit, activate, and close campaigns; build the submission form', 'Campaign', 'FUNCTIONAL'),
    ('campaign.submit', 'Submit Campaign Recipients', 'Fill and submit recipient entries against campaigns in their audience', 'Campaign', 'FUNCTIONAL'),
    ('campaign.approve', 'Approve Campaign Submissions', 'Approve or reject submitted recipient entries', 'Campaign', 'FUNCTIONAL');
