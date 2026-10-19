-- A company can now onboard its own vendor(s) rather than only the platform admin doing it
-- centrally (vendor.manage stays platform-only/company_assignable=false -- this is a
-- deliberately narrower key: create + view your own roster, not edit/suspend/commission
-- control over any vendor platform-wide).
INSERT INTO access_controls (key, name, description, category, type, company_assignable) VALUES
    ('company.vendor.manage', 'Manage Own Vendors', 'Add new vendors and view the vendors serving your own company''s Gift Store', 'Vendor', 'FUNCTIONAL', true);
