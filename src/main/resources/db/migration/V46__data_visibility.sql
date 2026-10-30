-- Data visibility: which records a user can see is decided by where they sit, not by a
-- permission. A user sees records they own, records owned by anyone below them in the
-- reporting line, records stamped with an org unit below their own, records in the units they
-- additionally cover (and below), and -- if company_wide -- everything in the company.
-- See service.VisibilityService.

-- 1. "Whole company" becomes an explicit placement instead of the org.scope.all permission:
--    whoever effectively held it (role or designation grant, or a user GRANT, and no user
--    REVOKE) is now company-wide. Then the key goes, with its grants.
ALTER TABLE users ADD COLUMN company_wide BOOLEAN NOT NULL DEFAULT false;

UPDATE users u SET company_wide = true
WHERE u.company_id IS NOT NULL
  AND (
        EXISTS (SELECT 1 FROM role_access_controls rac JOIN access_controls ac ON ac.id = rac.access_control_id
                WHERE rac.role_id = u.role_id AND ac.key = 'org.scope.all')
     OR EXISTS (SELECT 1 FROM designation_access_controls dac JOIN access_controls ac ON ac.id = dac.access_control_id
                WHERE dac.company_id = u.company_id AND dac.designation = u.designation AND ac.key = 'org.scope.all')
     OR EXISTS (SELECT 1 FROM user_access_controls uac JOIN access_controls ac ON ac.id = uac.access_control_id
                WHERE uac.user_id = u.id AND uac.effect = 'GRANT' AND ac.key = 'org.scope.all')
  )
  AND NOT EXISTS (SELECT 1 FROM user_access_controls uac JOIN access_controls ac ON ac.id = uac.access_control_id
                  WHERE uac.user_id = u.id AND uac.effect = 'REVOKE' AND ac.key = 'org.scope.all');

DELETE FROM access_controls WHERE key = 'org.scope.all';

-- Every permission now has its grid position (V45).
ALTER TABLE access_controls ALTER COLUMN module SET NOT NULL;
ALTER TABLE access_controls ALTER COLUMN action SET NOT NULL;

-- 2. Whose an order is. created_by is whoever turned it into an order (an approver, for an
--    approved request); the owner is the person it was placed for: the request's requester,
--    the campaign entry's submitter, else its creator.
ALTER TABLE orders ADD COLUMN owner_id UUID REFERENCES users (id);

UPDATE orders o SET owner_id = COALESCE(
    (SELECT q.requested_by FROM quick_gift_requests q WHERE q.order_id = o.id),
    (SELECT s.submitted_by FROM submissions s WHERE s.id = o.submission_id),
    o.created_by);

ALTER TABLE orders ALTER COLUMN owner_id SET NOT NULL;
CREATE INDEX idx_orders_owner_id ON orders (owner_id);

-- 3. Campaign entries carry the submitter's org unit, like requests and orders already do.
ALTER TABLE submissions ADD COLUMN org_unit_id UUID REFERENCES org_units (id);

UPDATE submissions s SET org_unit_id = u.org_unit_id FROM users u WHERE u.id = s.submitted_by;

CREATE INDEX idx_submissions_org_unit_id ON submissions (org_unit_id);
