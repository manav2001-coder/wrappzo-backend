-- Approving up the artwork approval cycle is separate from the MR's own first review (artwork.review).
-- approval.decide = approve or send back a proof at a manager level. Whoever may open the Approvals page today
-- (approval.view) keeps being able to act in it; MR/AM, who only review from the Orders page, do not get it.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('approval.decide', 'approval', 'decide', 'Approvals', 'Approve artwork', 'Approve or send back an artwork proof at your level of the approval cycle', 'FUNCTIONAL', true, 610);

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT DISTINCT tac.template_id, decide.id
FROM template_access_controls tac
JOIN access_controls view_key ON view_key.id = tac.access_control_id AND view_key.key = 'approval.view'
JOIN access_controls decide ON decide.key = 'approval.decide'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT DISTINCT c.company_id, decide.id, true
FROM company_access_controls c
JOIN access_controls view_key ON view_key.id = c.access_control_id AND view_key.key = 'approval.view' AND c.is_active
JOIN access_controls decide ON decide.key = 'approval.decide'
ON CONFLICT DO NOTHING;
