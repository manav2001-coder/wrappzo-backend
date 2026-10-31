-- Menu names are set by platform staff for each company (Platform config > Menu config), not by a
-- company's own admins: the company-assignable menu.edit key goes, with every grant of it.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('platform_menu_config.view', 'platform_menu_config', 'view', 'Menu config', 'View', 'See the names each company gives its navigation menus', 'FUNCTIONAL', false, 2600),
    ('platform_menu_config.edit', 'platform_menu_config', 'edit', 'Menu config', 'Edit', 'Rename a company''s navigation menus', 'FUNCTIONAL', false, 2601);

DELETE FROM access_controls WHERE key = 'menu.edit';
