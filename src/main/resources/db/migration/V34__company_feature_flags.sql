-- Per-company toggles for optional workflow changes (warehouse logistics, the Marketing
-- artwork-approval gate, etc.) -- see the client proposal's "Important Implementation
-- Principle": these must be introduced without breaking any company's existing workflow,
-- so absence of a row for a given flag_key means OFF, not an error. Everything downstream
-- checks CompanyFeatureFlagService.isEnabled() rather than assuming a row exists.
CREATE TABLE company_feature_flags (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    flag_key   VARCHAR(64) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_feature_flags_company_key UNIQUE (company_id, flag_key)
);
