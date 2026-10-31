-- Access control the way MobixFL has it: a permission key per Access Control screen and action
-- (view / add / edit for the two masters; manage for opening an assignment screen and assignment
-- for writing in it), templates with a key, icon and path, and an audit trail on what is edited.
--
-- The generic template.* and access.* keys (V45, V47) are replaced by the per-screen keys below.
-- Anything that carried the old key keeps the equivalent new ones, so nobody loses or gains access.

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('tbac.feature_master.view', 'tbac', 'feature_master.view', 'Access control', 'Feature master: View', 'See the features the company has registered', 'FUNCTIONAL', true, 1000),
    ('tbac.feature_master.add', 'tbac', 'feature_master.add', 'Access control', 'Feature master: Add', 'Register features for the company', 'FUNCTIONAL', true, 1001),
    ('tbac.feature_master.edit', 'tbac', 'feature_master.edit', 'Access control', 'Feature master: Edit', 'Switch the company''s features on and off', 'FUNCTIONAL', true, 1002),
    ('tbac.template_master.view', 'tbac', 'template_master.view', 'Access control', 'Template master: View', 'See access templates', 'FUNCTIONAL', true, 1010),
    ('tbac.template_master.add', 'tbac', 'template_master.add', 'Access control', 'Template master: Add', 'Create access templates', 'FUNCTIONAL', true, 1011),
    ('tbac.template_master.edit', 'tbac', 'template_master.edit', 'Access control', 'Template master: Edit', 'Change and deactivate access templates', 'FUNCTIONAL', true, 1012),
    ('tbac.features_to_template.manage', 'tbac', 'features_to_template.manage', 'Access control', 'Features to template: Open', 'Open the features-to-template screens', 'FUNCTIONAL', true, 1020),
    ('tbac.features_to_template.assignment', 'tbac', 'features_to_template.assignment', 'Access control', 'Features to template: Assign', 'Add and remove a template''s features', 'FUNCTIONAL', true, 1021),
    ('tbac.templates_to_template.manage', 'tbac', 'templates_to_template.manage', 'Access control', 'Templates to template: Open', 'Open the templates-to-template screens', 'FUNCTIONAL', true, 1030),
    ('tbac.templates_to_template.assignment', 'tbac', 'templates_to_template.assignment', 'Access control', 'Templates to template: Assign', 'Add and remove the templates a template includes', 'FUNCTIONAL', true, 1031),
    ('tbac.user_template.manage', 'tbac', 'user_template.manage', 'Access control', 'Templates to user: Open', 'Open the templates-to-user screens', 'FUNCTIONAL', true, 1040),
    ('tbac.user_template.assignment', 'tbac', 'user_template.assignment', 'Access control', 'Templates to user: Assign', 'Give a user templates and take them away', 'FUNCTIONAL', true, 1041),
    ('tbac.bulk_assign_user_templates', 'tbac', 'bulk_assign_user_templates', 'Access control', 'Templates to user: Bulk assign', 'Give several users the same templates at once', 'FUNCTIONAL', true, 1042),
    ('tbac.role_template.manage', 'tbac', 'role_template.manage', 'Access control', 'Templates to role: Open', 'Open the templates-to-role screens', 'FUNCTIONAL', true, 1050),
    ('tbac.role_template.assignment', 'tbac', 'role_template.assignment', 'Access control', 'Templates to role: Assign', 'Give a role templates and take them away', 'FUNCTIONAL', true, 1051);

-- Old key -> the new keys that replace it.
CREATE TEMPORARY TABLE tbac_key_map (old_key VARCHAR(255) NOT NULL, new_key VARCHAR(255) NOT NULL);
INSERT INTO tbac_key_map (old_key, new_key) VALUES
    ('template.view', 'tbac.feature_master.view'),
    ('template.view', 'tbac.template_master.view'),
    ('template.view', 'tbac.features_to_template.manage'),
    ('template.view', 'tbac.templates_to_template.manage'),
    ('template.edit', 'tbac.feature_master.add'),
    ('template.edit', 'tbac.feature_master.edit'),
    ('template.edit', 'tbac.template_master.add'),
    ('template.edit', 'tbac.template_master.edit'),
    ('template.edit', 'tbac.features_to_template.assignment'),
    ('template.edit', 'tbac.templates_to_template.assignment'),
    ('access.view', 'tbac.user_template.manage'),
    ('access.view', 'tbac.role_template.manage'),
    ('access.edit', 'tbac.user_template.assignment'),
    ('access.edit', 'tbac.role_template.assignment'),
    ('access.edit', 'tbac.bulk_assign_user_templates');

-- What templates grant...
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT tac.template_id, new_ac.id
FROM template_access_controls tac
JOIN access_controls old_ac ON old_ac.id = tac.access_control_id
JOIN tbac_key_map km ON km.old_key = old_ac.key
JOIN access_controls new_ac ON new_ac.key = km.new_key
ON CONFLICT DO NOTHING;

-- ...and which features each company has registered, keeping the old key's on/off state.
INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT cac.company_id, new_ac.id, cac.is_active
FROM company_access_controls cac
JOIN access_controls old_ac ON old_ac.id = cac.access_control_id
JOIN tbac_key_map km ON km.old_key = old_ac.key
JOIN access_controls new_ac ON new_ac.key = km.new_key
ON CONFLICT DO NOTHING;

-- Every grant that pointed at an old key goes with it (all of them cascade).
DELETE FROM access_controls WHERE key IN ('template.view', 'template.edit', 'access.view', 'access.edit');

DROP TABLE tbac_key_map;

-- Templates: a derived key (company number, or "system", and the snake_cased name -- set once on
-- create, never re-derived on rename), UI hints, and who last touched it.
ALTER TABLE templates ADD COLUMN template_key VARCHAR(255);
ALTER TABLE templates ADD COLUMN icon VARCHAR(50);
ALTER TABLE templates ADD COLUMN path VARCHAR(255);
ALTER TABLE templates ADD COLUMN created_by_name VARCHAR(200);
ALTER TABLE templates ADD COLUMN updated_by_name VARCHAR(200);

UPDATE templates t
SET template_key = 'c' || c.company_no::text || '_' || trim(both '_' from lower(regexp_replace(t.name, '[^a-zA-Z0-9]+', '_', 'g')))
FROM companies c
WHERE t.company_id = c.id;

UPDATE templates
SET template_key = 'system_' || trim(both '_' from lower(regexp_replace(name, '[^a-zA-Z0-9]+', '_', 'g')))
WHERE company_id IS NULL;

ALTER TABLE templates ALTER COLUMN template_key SET NOT NULL;

-- The feature master's audit trail: when a company registered a feature and last changed it, and who.
ALTER TABLE company_access_controls ADD COLUMN added_on TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE company_access_controls ADD COLUMN added_by_name VARCHAR(200);
ALTER TABLE company_access_controls ADD COLUMN updated_on TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE company_access_controls ADD COLUMN updated_by_name VARCHAR(200);
