-- Vendor (printer) names are confidential: only the company admin sees them (platform staff have their own vendor screens).
-- Take company_vendor.view away from every template the Company Admin does not itself use -- so HO, ZM, RM and AM no longer
-- hold it -- while a template that Company Admin includes (directly or through others) keeps it.
WITH RECURSIVE admin_roots AS (
    SELECT t.id FROM templates t WHERE t.company_id IS NULL AND t.name = 'Company Admin'
    UNION
    SELECT rt.template_id FROM role_templates rt JOIN roles r ON r.id = rt.role_id WHERE r.name = 'Company Admin'
), admin_templates AS (
    SELECT id FROM admin_roots
    UNION
    SELECT tr.referenced_template_id FROM template_references tr JOIN admin_templates a ON a.id = tr.template_id
)
DELETE FROM template_access_controls tac
USING access_controls ac
WHERE tac.access_control_id = ac.id
  AND ac.key = 'company_vendor.view'
  AND tac.template_id NOT IN (SELECT id FROM admin_templates);
