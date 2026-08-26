-- Some access-control keys are inherently platform-wide (Super Admin's own operations across
-- every company) and must never be grantable to a single company's designation -- most
-- notably order.manage, which sees and reassigns EVERY company's fulfillment, not just one.
-- A company's own order visibility already works without this key: /api/orders is gated by
-- campaign.manage/campaign.submit/campaign.approve and is scoped to the caller's own company.
ALTER TABLE access_controls ADD COLUMN company_assignable BOOLEAN NOT NULL DEFAULT true;

UPDATE access_controls SET company_assignable = false
WHERE key IN ('company.manage', 'vendor.manage', 'order.manage', 'gift.manage');

-- Purge anything already granted to a designation before this restriction existed.
DELETE FROM designation_access_controls dac
USING access_controls ac
WHERE dac.access_control_id = ac.id AND ac.company_assignable = false;
