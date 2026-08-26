-- Company Admin now gets full campaign rights (create/manage, submit, approve) by default,
-- not just user.manage/role.manage. Backfill this onto every company's existing "Company
-- Admin" role so companies created before this change aren't stuck with the old, narrower
-- grant list -- new companies get it directly via CompanyService's seeding list.
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.name = 'Company Admin'
  AND r.company_id IS NOT NULL
  AND ac.key IN ('campaign.manage', 'campaign.submit', 'campaign.approve')
  AND NOT EXISTS (
      SELECT 1 FROM role_access_controls rac
      WHERE rac.role_id = r.id AND rac.access_control_id = ac.id
  );
