-- Locking the role model down to exactly two roles per company: Company Admin (full
-- campaign rights) and Member (audience-scoped visibility + submit recipients/orders for
-- review). Member previously had zero access-control keys, so no one in it could reach
-- any campaign/submission endpoint at all. Grant campaign.submit -- audience/DRAFT
-- filtering (CampaignService.isInAudience, SubmissionService.requireInAudience) already
-- restricts what a Member with just this key can see and act on.
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.name = 'Member'
  AND r.company_id IS NOT NULL
  AND ac.key = 'campaign.submit'
  AND NOT EXISTS (
      SELECT 1 FROM role_access_controls rac
      WHERE rac.role_id = r.id AND rac.access_control_id = ac.id
  );
