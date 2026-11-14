-- Seeing what a gift costs -- the price on the Gift Store, on orders and on the approval queue -- is its own permission,
-- price.view. Only the Company Admin holds it: granted to the system Company Admin template and to every template the
-- Company Admin's roles use (and the templates those include), so the AMs, RMs, ZMs and HO no longer see a price.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('price.view', 'price', 'view', 'Pricing', 'See prices', 'See what a gift costs, on the Gift Store, orders and approvals. Keep this to the company admin: prices are confidential', 'FUNCTIONAL', true, 613);

WITH RECURSIVE admin_roots AS (
    SELECT t.id FROM templates t WHERE t.company_id IS NULL AND t.name = 'Company Admin'
    UNION
    SELECT rt.template_id FROM role_templates rt JOIN roles r ON r.id = rt.role_id WHERE r.name = 'Company Admin'
), admin_templates AS (
    SELECT id FROM admin_roots
    UNION
    SELECT tr.referenced_template_id FROM template_references tr JOIN admin_templates a ON a.id = tr.template_id
)
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT a.id, ac.id
FROM admin_templates a
JOIN access_controls ac ON ac.key = 'price.view'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT c.id, ac.id, true
FROM companies c
JOIN access_controls ac ON ac.key = 'price.view'
ON CONFLICT DO NOTHING;
