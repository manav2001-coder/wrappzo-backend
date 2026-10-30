-- Template-based access control (TBAC). A template is a named bundle of access-control keys that
-- can include other templates; a role (and, for one-off exceptions, a user) is mapped to templates,
-- and templates are the only thing that grants. company_id NULL = a system master template, shared
-- by every company and editable by platform staff only. A company may clone one or build its own.
--
-- Additive on purpose: role_access_controls, designation_access_controls and user_access_controls
-- stay in place (nothing reads them any more) so the previous release keeps working through the
-- deploy; a later migration drops them.

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('role.view', 'role', 'view', 'Roles', 'View', 'See the company''s roles', 'FUNCTIONAL', true, 1050),
    ('role.edit', 'role', 'edit', 'Roles', 'Edit', 'Create, rename and remove roles, and choose the templates each role carries', 'FUNCTIONAL', true, 1051),
    ('template.view', 'template', 'view', 'Access templates', 'View', 'See access templates and what they grant', 'FUNCTIONAL', true, 1060),
    ('template.edit', 'template', 'edit', 'Access templates', 'Edit', 'Create, clone and change access templates', 'FUNCTIONAL', true, 1061);

CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID REFERENCES companies (id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_templates_company_name ON templates (company_id, name) WHERE company_id IS NOT NULL;
CREATE UNIQUE INDEX uq_templates_system_name ON templates (name) WHERE company_id IS NULL;

CREATE TABLE template_access_controls (
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    access_control_id UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    PRIMARY KEY (template_id, access_control_id)
);

CREATE TABLE template_references (
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    referenced_template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    PRIMARY KEY (template_id, referenced_template_id),
    CHECK (template_id <> referenced_template_id)
);
CREATE INDEX idx_template_references_referenced ON template_references (referenced_template_id);

CREATE TABLE role_templates (
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, template_id)
);
CREATE INDEX idx_role_templates_template ON role_templates (template_id);

CREATE TABLE user_templates (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, template_id)
);
CREATE INDEX idx_user_templates_template ON user_templates (template_id);

-- A REVOKE override cannot be expressed (templates only add). Nothing writes them today; refuse to
-- guess if one exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM user_access_controls WHERE effect = 'REVOKE') THEN
        RAISE EXCEPTION 'user_access_controls holds REVOKE overrides, which templates cannot express; resolve them before migrating';
    END IF;
END $$;

-- Every existing role becomes a template of the same name carrying the role's grants (the platform
-- Super Admin keeps its "everything" flag instead).
INSERT INTO templates (company_id, name, description)
SELECT r.company_id, r.name, r.description FROM roles r WHERE NOT r.is_platform_super_admin;

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, rac.access_control_id
FROM roles r
JOIN templates t ON t.name = r.name AND t.company_id IS NOT DISTINCT FROM r.company_id
JOIN role_access_controls rac ON rac.role_id = r.id
WHERE NOT r.is_platform_super_admin;

INSERT INTO role_templates (role_id, template_id)
SELECT r.id, t.id
FROM roles r
JOIN templates t ON t.name = r.name AND t.company_id IS NOT DISTINCT FROM r.company_id
WHERE NOT r.is_platform_super_admin;

-- A designation that granted anything becomes a template given to the users holding it (designations
-- stop being an access concept; roles are the only one).
INSERT INTO templates (company_id, name, description)
SELECT DISTINCT dac.company_id, 'Designation: ' || dac.designation, 'Migrated from the access granted to the designation "' || dac.designation || '"'
FROM designation_access_controls dac;

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, dac.access_control_id
FROM designation_access_controls dac
JOIN templates t ON t.company_id = dac.company_id AND t.name = 'Designation: ' || dac.designation;

INSERT INTO user_templates (user_id, template_id)
SELECT u.id, t.id
FROM users u
JOIN templates t ON t.company_id = u.company_id AND t.name = 'Designation: ' || u.designation
ON CONFLICT DO NOTHING;

-- Per-user GRANT overrides likewise become one template per user.
INSERT INTO templates (company_id, name, description)
SELECT DISTINCT u.company_id, 'Individual access: ' || u.id, 'Migrated from ' || u.email || '''s individual grants'
FROM user_access_controls uac JOIN users u ON u.id = uac.user_id
WHERE uac.effect = 'GRANT';

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, uac.access_control_id
FROM user_access_controls uac
JOIN users u ON u.id = uac.user_id
JOIN templates t ON t.company_id IS NOT DISTINCT FROM u.company_id AND t.name = 'Individual access: ' || u.id
WHERE uac.effect = 'GRANT';

INSERT INTO user_templates (user_id, template_id)
SELECT u.id, t.id
FROM users u
JOIN templates t ON t.company_id IS NOT DISTINCT FROM u.company_id AND t.name = 'Individual access: ' || u.id
ON CONFLICT DO NOTHING;

-- Existing Company Admins gain the screens that manage roles and templates.
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, ac.id
FROM templates t
JOIN roles r ON r.name = t.name AND r.company_id IS NOT DISTINCT FROM t.company_id
CROSS JOIN access_controls ac
WHERE r.name = 'Company Admin' AND t.company_id IS NOT NULL
  AND ac.key IN ('role.view', 'role.edit', 'template.view', 'template.edit')
ON CONFLICT DO NOTHING;

-- The system master template a new company's Company Admin role starts from.
INSERT INTO templates (company_id, name, description) VALUES (NULL, 'Company Admin', 'Everything a company can hold');

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, ac.id
FROM templates t CROSS JOIN access_controls ac
WHERE t.company_id IS NULL AND t.name = 'Company Admin' AND ac.company_assignable;
