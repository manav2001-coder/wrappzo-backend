-- Approving another MR's doctor change request was gated on recipient.manage, which every
-- Member holds (V27) -- so any MR could approve any request. Company-wide approval now has its
-- own key, granted to Company Admin only; a company can hand it to other designations too.
-- The requester's direct manager (users.reports_to_id) still approves without it.
INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('recipient.approve', 'Approve Doctor Changes', 'Approve or reject any MR''s request to add, edit or remove a doctor, not just your direct reports''', 'Recipients', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name = 'Company Admin'
  AND ac.key = 'recipient.approve'
ON CONFLICT DO NOTHING;
