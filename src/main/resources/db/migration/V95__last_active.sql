-- When each person and vendor last used the app, for the Super Admin's Users and Vendors screens.
-- Stamped at sign-in and each time the app renews its 15-minute sign-in (so it is accurate to about 15 minutes of use, with
-- no extra write per request). NULL means never signed in.
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMPTZ;
ALTER TABLE vendors ADD COLUMN last_active_at TIMESTAMPTZ;

-- Start from what the session records already know, so the column is not blank for everyone on the day it ships.
UPDATE users u SET last_active_at = t.latest
FROM (SELECT user_id, max(created_at) AS latest FROM refresh_tokens GROUP BY user_id) t
WHERE t.user_id = u.id;
UPDATE vendors v SET last_active_at = t.latest
FROM (SELECT vendor_id, max(created_at) AS latest FROM vendor_refresh_tokens GROUP BY vendor_id) t
WHERE t.vendor_id = v.id;

CREATE INDEX idx_users_company_last_active ON users (company_id, last_active_at);

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('platform_user_activity.view', 'platform_user_activity', 'view', 'Activity', 'View', 'See when each user and vendor was last active', 'FUNCTIONAL', false, 2800);
