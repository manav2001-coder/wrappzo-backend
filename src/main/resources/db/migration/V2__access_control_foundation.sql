-- Access-control foundation: companies, the generic org-unit tree, the
-- access-control catalog (functional + UI keys), roles, and users, plus the
-- role-level and per-user-override mappings onto the access-control catalog.

CREATE TABLE companies (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Generic hierarchy tree (Division/Zone/Region/Area or whatever a given
-- company calls its levels). Optional per company — a company that has no
-- field-force hierarchy simply has no rows here.
CREATE TABLE org_units (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES org_units (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    level_name   VARCHAR(100) NOT NULL,
    -- Every ancestor's id, root-first, excluding this row itself. Maintained
    -- at creation time (parent's ancestor_ids + parent's own id) so "everyone
    -- under node X" is a single indexed query at any tree depth, instead of a
    -- variable-depth join chain.
    ancestor_ids UUID[]      NOT NULL DEFAULT '{}',
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parent_id, name)
);

CREATE INDEX idx_org_units_company_id ON org_units (company_id);
CREATE INDEX idx_org_units_ancestor_ids ON org_units USING GIN (ancestor_ids);

-- The fixed, well-defined catalog of grantable capabilities. FUNCTIONAL keys
-- are enforced server-side (the real security boundary); UI keys only ever
-- control frontend visibility and must never be relied on to protect an
-- endpoint.
CREATE TABLE access_controls (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(150) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    category    VARCHAR(100) NOT NULL,
    type        VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_access_controls_type CHECK (type IN ('FUNCTIONAL', 'UI'))
);

-- A named bundle of access-control keys. company_id is null for system-wide
-- (platform) roles; otherwise the role belongs to exactly one company.
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID REFERENCES companies (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_roles_company_id ON roles (company_id);

-- Plain UNIQUE(company_id, name) wouldn't stop two platform roles both named
-- "Super Admin" — SQL treats NULL <> NULL, so null-company rows never
-- collide. Two partial indexes instead, one per case.
CREATE UNIQUE INDEX uq_roles_company_name ON roles (company_id, name) WHERE company_id IS NOT NULL;
CREATE UNIQUE INDEX uq_roles_platform_name ON roles (name) WHERE company_id IS NULL;

-- Baseline: which access-control keys a role grants.
CREATE TABLE role_access_controls (
    role_id           UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    access_control_id UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, access_control_id)
);

CREATE INDEX idx_role_access_controls_access_control_id ON role_access_controls (access_control_id);

-- A Wrapzzo user. company_id is null for Wrapzzo's own platform staff.
-- Application-level invariant (not enforceable as a simple SQL constraint):
-- a user's role must belong to that same company_id, or be a null-company
-- role for a null-company user.
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID REFERENCES companies (id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles (id),
    org_unit_id     UUID REFERENCES org_units (id) ON DELETE SET NULL,
    reports_to_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_company_id ON users (company_id);
CREATE INDEX idx_users_role_id ON users (role_id);
CREATE INDEX idx_users_org_unit_id ON users (org_unit_id);
CREATE INDEX idx_users_reports_to_id ON users (reports_to_id);

-- Per-user override on top of the role baseline. GRANT adds a key the user's
-- role doesn't give them; REVOKE takes away a key their role would otherwise
-- grant. REVOKE always wins over the role baseline.
CREATE TABLE user_access_controls (
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_control_id UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    effect            VARCHAR(16) NOT NULL,
    granted_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, access_control_id),
    CONSTRAINT chk_user_access_controls_effect CHECK (effect IN ('GRANT', 'REVOKE'))
);

CREATE INDEX idx_user_access_controls_access_control_id ON user_access_controls (access_control_id);
