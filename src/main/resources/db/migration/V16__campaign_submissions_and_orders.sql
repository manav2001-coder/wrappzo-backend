-- A Campaign's submission form (1:1). Seeded with the PRD 2.6 default field set at
-- campaign-creation time (see DefaultCampaignFields) -- the campaign creator can then
-- edit it freely before activating.
CREATE TABLE campaign_forms (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID        NOT NULL UNIQUE REFERENCES campaigns (id) ON DELETE CASCADE,
    version     INTEGER     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaign_fields (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_form_id UUID         NOT NULL REFERENCES campaign_forms (id) ON DELETE CASCADE,
    field_key        VARCHAR(100) NOT NULL,
    label            VARCHAR(255) NOT NULL,
    field_type       VARCHAR(16)  NOT NULL,
    required         BOOLEAN      NOT NULL DEFAULT false,
    placeholder      VARCHAR(255),
    options          TEXT[]       NOT NULL DEFAULT '{}',
    display_order    INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (campaign_form_id, field_key)
);

CREATE INDEX idx_campaign_fields_campaign_form_id ON campaign_fields (campaign_form_id);

-- One recipient entry submitted against a Campaign (PRD 2.6/2.7). Its answers live in
-- submission_values, keyed by campaign_fields -- this row is just the workflow envelope.
CREATE TABLE submissions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    campaign_id      UUID        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    submitted_by     UUID        NOT NULL REFERENCES users (id),
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    rejection_reason TEXT,
    reviewed_by      UUID        REFERENCES users (id),
    reviewed_at      TIMESTAMPTZ,
    submitted_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_submissions_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'RESUBMITTED', 'APPROVED'))
);

CREATE INDEX idx_submissions_campaign_id ON submissions (campaign_id);
CREATE INDEX idx_submissions_submitted_by ON submissions (submitted_by);
CREATE INDEX idx_submissions_company_id_status ON submissions (company_id, status);

CREATE TABLE submission_values (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id     UUID   NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    campaign_field_id UUID   NOT NULL REFERENCES campaign_fields (id) ON DELETE RESTRICT,
    value_text        TEXT,
    value_number      DOUBLE PRECISION,
    value_boolean     BOOLEAN,
    value_date        DATE,
    value_list        TEXT[] NOT NULL DEFAULT '{}',
    UNIQUE (submission_id, campaign_field_id)
);

CREATE INDEX idx_submission_values_submission_id ON submission_values (submission_id);

CREATE TABLE submission_attachments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id     UUID         NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    campaign_field_id UUID         NOT NULL REFERENCES campaign_fields (id) ON DELETE RESTRICT,
    file_key          VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    file_size         BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_submission_attachments_submission_id ON submission_attachments (submission_id);

-- The commercial transaction (PRD 2.10) -- created exactly once per approved submission.
-- submission_id is UNIQUE: the idempotency guard against a double-approve creating two
-- Orders (PRD 4.6). No fulfillment/artwork/QC/vendor-dispatch columns yet -- that's a
-- later pass (WorkOrder), kept deliberately out of this table.
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    order_no        INTEGER     GENERATED ALWAYS AS IDENTITY,
    campaign_id     UUID        NOT NULL REFERENCES campaigns (id),
    submission_id   UUID        NOT NULL UNIQUE REFERENCES submissions (id),
    gift_id         UUID        REFERENCES gifts (id),
    recipient_name  VARCHAR(255),
    recipient_phone VARCHAR(50),
    source          VARCHAR(16) NOT NULL DEFAULT 'CAMPAIGN',
    status          VARCHAR(24) NOT NULL DEFAULT 'PROCESSING',
    created_by      UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_orders_source CHECK (source IN ('CAMPAIGN', 'QUICK_GIFT', 'BULK_GIFT')),
    CONSTRAINT chk_orders_status CHECK (status IN ('PROCESSING', 'PRODUCTION_QUEUE', 'DISPATCHED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_orders_company_id ON orders (company_id);
CREATE INDEX idx_orders_campaign_id ON orders (campaign_id);
CREATE INDEX idx_orders_company_id_status ON orders (company_id, status);
