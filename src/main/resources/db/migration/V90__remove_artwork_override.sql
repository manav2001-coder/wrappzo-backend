-- The artwork approval cycle is strict again: the AM's review, then level 1 (the RM), then level 2 (the HO), each in turn.
-- Nobody approves in place of a lower level, so the permission that allowed it and the "skipped" step in a proof's trail go.
-- (V82, which added them, has already run everywhere and stays as it is.)

-- A skipped step was only ever a marker beside the approval that skipped it; the approval itself is kept.
DELETE FROM order_design_approvals WHERE decision = 'BYPASSED';
ALTER TABLE order_design_approvals DROP CONSTRAINT chk_order_design_approvals_decision;
ALTER TABLE order_design_approvals ADD CONSTRAINT chk_order_design_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED'));

DELETE FROM role_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'artwork.override');
DELETE FROM template_access_controls    WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'artwork.override');
DELETE FROM designation_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'artwork.override');
DELETE FROM user_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'artwork.override');
DELETE FROM company_access_controls     WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'artwork.override');
DELETE FROM access_controls WHERE key = 'artwork.override';
