-- Who approves a proof after the AM, as an ordered list of steps rather than a bare count of managers.
-- Each step is a role and where to find the person holding it for the order at hand:
--   REPORTING_LINE  the nearest manager above the AM who holds that role (an RM, say)
--   DIVISION        anyone holding that role in the AM's division -- the first to act decides (an HO, say)
-- A company with no steps keeps the old behaviour: companies.artwork_approval_levels managers up the AM's line.
CREATE TABLE company_artwork_approval_steps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    step_no     INTEGER     NOT NULL CHECK (step_no >= 1),
    role_id     UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    scope       VARCHAR(20) NOT NULL CHECK (scope IN ('REPORTING_LINE', 'DIVISION')),
    UNIQUE (company_id, step_no)
);
