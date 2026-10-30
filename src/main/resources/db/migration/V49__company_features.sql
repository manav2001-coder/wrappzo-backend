-- Per-company feature master, replacing V48's global switch.
--
-- V47 and V48 had already been applied to shared databases when the design changed, so they stay
-- exactly as they ran (editing an applied migration makes Flyway refuse to start); this one moves
-- the schema to where it should be.

-- V48's single global is_active flag is superseded by the per-company switch below.
ALTER TABLE access_controls DROP COLUMN is_active;

-- V47 seeded a system "Member" template for a default Member role. There is no default role any
-- more -- a company's roles are its own -- so remove it, unless something already uses it.
DELETE FROM templates t
WHERE t.company_id IS NULL AND t.name = 'Member'
  AND NOT EXISTS (SELECT 1 FROM role_templates rt WHERE rt.template_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM user_templates ut WHERE ut.template_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM template_references tr WHERE tr.referenced_template_id = t.id);

-- Feature master, per company: which permission keys a company has registered, and whether each is
-- switched on. A company's people can hold only its active features, whatever a template carries
-- (see AccessControlCacheService), and a company template may carry only those. New keys reach
-- existing companies at startup (CompanyFeatureRegistrar), new companies when they are created;
-- an admin who switches a feature off keeps it off, since registering only ever adds.
CREATE TABLE company_access_controls (
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    access_control_id UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (company_id, access_control_id)
);
CREATE INDEX idx_company_access_controls_feature ON company_access_controls (access_control_id);

INSERT INTO company_access_controls (company_id, access_control_id)
SELECT c.id, ac.id FROM companies c CROSS JOIN access_controls ac WHERE ac.company_assignable;
