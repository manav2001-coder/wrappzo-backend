-- Platform-wide settings a Super Admin can change from the app (Platform config > Email settings), instead of an environment variable.
-- A key/value table: one row per setting.
CREATE TABLE platform_settings (
    key VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_name VARCHAR(200)
);

-- Whether the order-workflow emails (approver "waiting for you", reminders, decisions, the vendor's new-order and proof emails) are sent.
-- On, as it has always been; invites, password setup and the doctor photo request are never governed by this.
INSERT INTO platform_settings (key, value, updated_by_name) VALUES ('notification_emails_enabled', 'true', 'System');

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('platform_email_settings.view', 'platform_email_settings', 'view', 'Email settings', 'View', 'See whether the order-workflow emails are on or off', 'FUNCTIONAL', false, 2700),
    ('platform_email_settings.edit', 'platform_email_settings', 'edit', 'Email settings', 'Edit', 'Switch the order-workflow emails on or off for the whole platform', 'FUNCTIONAL', false, 2701);
