-- A senior in the artwork approval chain can approve a proof that is still waiting on a level below them: the MR's own review
-- is covered by level 1, and level 2 covers the MR's review and level 1 as well. Each level that was skipped is written to the
-- proof's approval trail as BYPASSED, by the person who skipped it.
ALTER TABLE order_design_approvals DROP CONSTRAINT chk_order_design_approvals_decision;
ALTER TABLE order_design_approvals ADD CONSTRAINT chk_order_design_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'BYPASSED'));

-- Who may do it is the company's to decide, per role. Only the access_controls row is inserted here: CompanyFeatureRegistrar
-- registers it for each company and CompanyAdminAccessReconciler hands it to the Company Admin template at startup.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('artwork.override', 'artwork', 'override', 'Artwork', 'Approve in place of lower levels',
     'Approve a proof that is still waiting on the MR or a lower level of the approval cycle, passing it on to the next level', 'FUNCTIONAL', true, 606);
