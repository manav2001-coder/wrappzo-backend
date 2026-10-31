-- The platform staff dashboard: one platform-only key (never grantable to a company).
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('platform_dashboard.view', 'platform_dashboard', 'view', 'Platform dashboard', 'View', 'See platform-wide counts: companies, vendors, users, products and orders', 'FUNCTIONAL', false, 2400);
