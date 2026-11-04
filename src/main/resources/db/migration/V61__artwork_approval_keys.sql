-- Who approves artwork after the AM is the company admin's to set (not Wrappzo's at onboarding, when the company's roles
-- do not exist yet). Two keys: see the steps, and change them. Only the Company Admin's template holds them by default;
-- a company can register them for others, a marketing head say.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('artwork_approval.view', 'artwork_approval', 'view', 'Artwork', 'Approval steps', 'See who approves artwork after the AM', 'FUNCTIONAL', true, 604),
    ('artwork_approval.configure', 'artwork_approval', 'configure', 'Artwork', 'Set approval steps', 'Choose who approves artwork after the AM, and in what order', 'FUNCTIONAL', true, 605);

-- The system master template every new company's Company Admin carries holds every key a company can hold.
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, ac.id
FROM templates t
JOIN access_controls ac ON ac.key IN ('artwork_approval.view', 'artwork_approval.configure')
WHERE t.company_id IS NULL AND t.name = 'Company Admin'
ON CONFLICT DO NOTHING;

-- And whatever template an existing Company Admin role carries (a company's own copy, say).
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT rt.template_id, ac.id
FROM role_templates rt
JOIN roles r ON r.id = rt.role_id AND r.name = 'Company Admin'
JOIN access_controls ac ON ac.key IN ('artwork_approval.view', 'artwork_approval.configure')
ON CONFLICT DO NOTHING;

-- Registered for every company that has a Company Admin role, switched on.
INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT r.company_id, ac.id, true
FROM roles r
JOIN access_controls ac ON ac.key IN ('artwork_approval.view', 'artwork_approval.configure')
WHERE r.name = 'Company Admin' AND r.company_id IS NOT NULL
ON CONFLICT DO NOTHING;
