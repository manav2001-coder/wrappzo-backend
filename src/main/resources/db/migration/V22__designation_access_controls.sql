-- Grants access-control keys to a company's designation (the free-text users.designation
-- value already populated by bulk import/manual add), independent of and additive to the
-- Role/role_access_controls system -- a user's effective permissions are their role's grants
-- UNION their designation's grants, resolved together in AccessControlCacheService.
CREATE TABLE designation_access_controls (
    company_id         UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    designation        VARCHAR(150) NOT NULL,
    access_control_id  UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, designation, access_control_id)
);

CREATE INDEX idx_designation_access_controls_access_control_id ON designation_access_controls (access_control_id);
