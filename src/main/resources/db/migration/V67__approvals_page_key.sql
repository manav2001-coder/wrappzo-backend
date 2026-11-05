-- The Approvals page (the manager-level queue of artwork proofs) gets a key of its own, approval.view. It is not the
-- approval-steps setup (artwork_approval.*, the company admin's), and not artwork.review, which every MR/AM holds to
-- review a proof from the Orders page -- so an MR/AM no longer reaches the queue. Granted to the Company Admin and to
-- every template that already lets its holders sign manager levels (artwork.final_approve); a company admin grants it to
-- any other approver's role under Templates.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('approval.view', 'approval', 'view', 'Approvals', 'View approvals', 'Open the Approvals page: artwork proofs waiting on a manager''s approval', 'FUNCTIONAL', true, 609);

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT t.id, ac.id
FROM templates t
JOIN access_controls ac ON ac.key = 'approval.view'
WHERE t.company_id IS NULL AND t.name = 'Company Admin'
ON CONFLICT DO NOTHING;

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT rt.template_id, ac.id
FROM role_templates rt
JOIN roles r ON r.id = rt.role_id AND r.name = 'Company Admin'
JOIN access_controls ac ON ac.key = 'approval.view'
ON CONFLICT DO NOTHING;

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT tac.template_id, view_key.id
FROM template_access_controls tac
JOIN access_controls final_key ON final_key.id = tac.access_control_id AND final_key.key = 'artwork.final_approve'
JOIN access_controls view_key ON view_key.key = 'approval.view'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT c.company_id, ac.id, true
FROM company_access_controls c
JOIN access_controls fin ON fin.id = c.access_control_id AND fin.key = 'artwork.final_approve' AND c.is_active
JOIN access_controls ac ON ac.key = 'approval.view'
ON CONFLICT DO NOTHING;
