-- Mapping vendors (printers) to divisions and org units is the company admin's job, not Wrappzo's: two keys, to map a
-- vendor to where it serves and to take a vendor off the company. Only the Company Admin's template holds them by default.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('company_vendor.assign', 'company_vendor', 'assign', 'Vendors', 'Map vendors', 'Choose which divisions and org units each vendor serves', 'FUNCTIONAL', true, 606),
    ('company_vendor.remove', 'company_vendor', 'remove', 'Vendors', 'Remove vendors', 'Take a vendor off the company''s roster', 'FUNCTIONAL', true, 607);

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, ac.id
FROM templates t
JOIN access_controls ac ON ac.key IN ('company_vendor.assign', 'company_vendor.remove')
WHERE t.company_id IS NULL AND t.name = 'Company Admin'
ON CONFLICT DO NOTHING;

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT rt.template_id, ac.id
FROM role_templates rt
JOIN roles r ON r.id = rt.role_id AND r.name = 'Company Admin'
JOIN access_controls ac ON ac.key IN ('company_vendor.assign', 'company_vendor.remove')
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT r.company_id, ac.id, true
FROM roles r
JOIN access_controls ac ON ac.key IN ('company_vendor.assign', 'company_vendor.remove')
WHERE r.name = 'Company Admin' AND r.company_id IS NOT NULL
ON CONFLICT DO NOTHING;
