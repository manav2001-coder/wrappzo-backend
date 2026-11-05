-- Seeing which vendor (printer) makes an order is its own permission, vendor_identity.view -- not the Vendors page
-- (company_vendor.view). Only the Company Admin holds it: granted to the system Company Admin template and to every
-- template the Company Admin's roles use (and the templates those include), so no one else gains it.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('vendor_identity.view', 'vendor_identity', 'view', 'Vendors', 'See vendor names', 'See which vendor makes an order, on orders and gifts. Keep this to the company admin: vendor names are confidential', 'FUNCTIONAL', true, 611);

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
JOIN access_controls ac ON ac.key = 'vendor_identity.view'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT c.company_id, ac.id, true
FROM company_access_controls c
JOIN access_controls vendors ON vendors.id = c.access_control_id AND vendors.key = 'company_vendor.view' AND c.is_active
JOIN access_controls ac ON ac.key = 'vendor_identity.view'
ON CONFLICT DO NOTHING;
