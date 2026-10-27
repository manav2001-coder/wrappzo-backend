-- A company's own names for its navigation menus (e.g. "Request Prescription Pads" for Quick
-- Gift). Keyed by the frontend's stable menu id; no row means the default name. One label per
-- menu, shown in every language -- a company renaming a menu wants its own term everywhere.
CREATE TABLE company_menu_labels (
    company_id UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    menu_id    VARCHAR(64) NOT NULL,
    label      VARCHAR(60) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, menu_id)
);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('menu.manage', 'Rename Menus', 'Give the menus in your company''s navigation your own names', 'Organisation', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND r.name = 'Company Admin'
  AND ac.key = 'menu.manage'
ON CONFLICT DO NOTHING;
