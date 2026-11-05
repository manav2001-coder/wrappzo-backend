-- V67 gave approval.view only to the Company Admin and templates holding artwork.final_approve, which left out the
-- managers who approve through the company's artwork approval steps (RM, HO ...). Give it to every role named in a
-- step -- through that role's templates, but only a template no other role also uses, so an MR/AM never gets it.
INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT rt.template_id, ac.id
FROM role_templates rt
JOIN access_controls ac ON ac.key = 'approval.view'
WHERE rt.role_id IN (SELECT role_id FROM company_artwork_approval_steps)
  AND NOT EXISTS (
      SELECT 1 FROM role_templates other
      WHERE other.template_id = rt.template_id
        AND other.role_id NOT IN (SELECT role_id FROM company_artwork_approval_steps))
ON CONFLICT DO NOTHING;

-- And make sure those companies have the feature key switched on, or the grant would be filtered out.
INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT s.company_id, ac.id, true
FROM company_artwork_approval_steps s
JOIN access_controls ac ON ac.key = 'approval.view'
ON CONFLICT DO NOTHING;
