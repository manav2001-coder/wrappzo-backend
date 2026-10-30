-- Action-level permissions: every feature-wide key ("campaign.manage") is split into the actions
-- it covered ("campaign.view", "campaign.create", ...), as module.action. The catalogue's code
-- side is security.Permission; PermissionCatalogVerifier checks the two match at startup.
--
-- category now holds the module's label and name the action's label; module/action/sort_order
-- let the access screen lay the catalogue out as a grid.
ALTER TABLE access_controls ADD COLUMN module VARCHAR(64);
ALTER TABLE access_controls ADD COLUMN action VARCHAR(64);
ALTER TABLE access_controls ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('campaign.view', 'campaign', 'view', 'Campaigns', 'View', 'See every campaign and its details, not just the ones you''re in the audience of', 'FUNCTIONAL', true, 200),
    ('campaign.create', 'campaign', 'create', 'Campaigns', 'Create', 'Start new campaigns', 'FUNCTIONAL', true, 201),
    ('campaign.edit', 'campaign', 'edit', 'Campaigns', 'Edit', 'Change a campaign''s details, form and audience', 'FUNCTIONAL', true, 202),
    ('campaign.publish', 'campaign', 'publish', 'Campaigns', 'Publish', 'Activate and close campaigns', 'FUNCTIONAL', true, 203),

    ('campaign_entry.submit', 'campaign_entry', 'submit', 'Campaign entries', 'Submit', 'Submit entries to the campaigns you''re in the audience of', 'FUNCTIONAL', true, 300),
    ('campaign_entry.view', 'campaign_entry', 'view', 'Campaign entries', 'View', 'See the entries submitted to a campaign', 'FUNCTIONAL', true, 301),

    ('request.create', 'request', 'create', 'Requests', 'Create', 'Place requests for products, including monthly repeats', 'FUNCTIONAL', true, 400),
    ('request.approve', 'request', 'approve', 'Requests', 'Approve', 'Approve or reject requests waiting for approval', 'FUNCTIONAL', true, 401),

    ('product.view', 'product', 'view', 'Product store', 'View', 'Browse the products available to you', 'FUNCTIONAL', true, 500),

    ('order.view', 'order', 'view', 'Orders', 'View', 'See orders and their progress', 'FUNCTIONAL', true, 600),
    ('order.artwork_review', 'order', 'artwork_review', 'Orders', 'Review artwork', 'Approve a vendor''s artwork proof or send it back with changes', 'FUNCTIONAL', true, 601),
    ('order.artwork_final', 'order', 'artwork_final', 'Orders', 'Final artwork approval', 'Sign any management level of artwork approval, covering for the manager it waits on', 'FUNCTIONAL', true, 602),

    ('recipient.view', 'recipient', 'view', 'Recipient directory', 'View', 'See the recipient directory', 'FUNCTIONAL', true, 700),
    ('recipient.create', 'recipient', 'create', 'Recipient directory', 'Create', 'Add recipients to the directory', 'FUNCTIONAL', true, 701),
    ('recipient.edit', 'recipient', 'edit', 'Recipient directory', 'Edit', 'Change recipients'' details', 'FUNCTIONAL', true, 702),

    ('recipient_list.manage', 'recipient_list', 'manage', 'My recipient list', 'Manage', 'Keep your own list of recipients and request additions, changes and removals', 'FUNCTIONAL', true, 800),
    ('recipient_list.approve', 'recipient_list', 'approve', 'My recipient list', 'Approve changes', 'Approve or reject anyone''s list changes, not just your direct reports''', 'FUNCTIONAL', true, 801),

    ('user.view', 'user', 'view', 'Users', 'View', 'See the company''s users', 'FUNCTIONAL', true, 900),
    ('user.create', 'user', 'create', 'Users', 'Invite', 'Add and bulk-import users', 'FUNCTIONAL', true, 901),
    ('user.edit', 'user', 'edit', 'Users', 'Edit', 'Change users'' placement, extra units and admin status', 'FUNCTIONAL', true, 902),
    ('user.delete', 'user', 'delete', 'Users', 'Delete', 'Remove users', 'FUNCTIONAL', true, 903),

    ('access.view', 'access', 'view', 'Access control', 'View', 'See what each designation can do', 'FUNCTIONAL', true, 1000),
    ('access.edit', 'access', 'edit', 'Access control', 'Edit', 'Change what each designation can do', 'FUNCTIONAL', true, 1001),

    ('company_vendor.view', 'company_vendor', 'view', 'Company vendors', 'View', 'See the vendors serving the company', 'FUNCTIONAL', true, 1100),
    ('company_vendor.add', 'company_vendor', 'add', 'Company vendors', 'Add', 'Onboard new vendors for the company', 'FUNCTIONAL', true, 1101),

    ('warehouse.view', 'warehouse', 'view', 'Warehouses', 'View', 'See warehouses and the orders routed through them', 'FUNCTIONAL', true, 1200),
    ('warehouse.setup', 'warehouse', 'setup', 'Warehouses', 'Set up', 'Add and change warehouses', 'FUNCTIONAL', true, 1201),
    ('warehouse.operate', 'warehouse', 'operate', 'Warehouses', 'Receive and deliver', 'Record orders received at a warehouse and sent out for delivery', 'FUNCTIONAL', true, 1202),

    ('menu.edit', 'menu', 'edit', 'Menu names', 'Edit', 'Give the company''s navigation menus its own names', 'FUNCTIONAL', true, 1300),

    ('platform_company.view', 'platform_company', 'view', 'Companies', 'View', 'See every company', 'FUNCTIONAL', false, 2000),
    ('platform_company.create', 'platform_company', 'create', 'Companies', 'Create', 'Onboard companies', 'FUNCTIONAL', false, 2001),
    ('platform_company.edit', 'platform_company', 'edit', 'Companies', 'Edit', 'Change a company''s details and logo', 'FUNCTIONAL', false, 2002),
    ('platform_company.status', 'platform_company', 'status', 'Companies', 'Activate', 'Activate and deactivate companies', 'FUNCTIONAL', false, 2003),
    ('platform_company.configure', 'platform_company', 'configure', 'Companies', 'Configure', 'Set a company''s features and vendors', 'FUNCTIONAL', false, 2004),

    ('platform_vendor.view', 'platform_vendor', 'view', 'Vendors', 'View', 'See every vendor', 'FUNCTIONAL', false, 2100),
    ('platform_vendor.create', 'platform_vendor', 'create', 'Vendors', 'Create', 'Onboard vendors', 'FUNCTIONAL', false, 2101),
    ('platform_vendor.edit', 'platform_vendor', 'edit', 'Vendors', 'Edit', 'Change vendors'' details and resend invites', 'FUNCTIONAL', false, 2102),
    ('platform_vendor.status', 'platform_vendor', 'status', 'Vendors', 'Suspend', 'Suspend and reactivate vendors', 'FUNCTIONAL', false, 2103),

    ('platform_product.view', 'platform_product', 'view', 'Product catalogue', 'View', 'See the whole product catalogue', 'FUNCTIONAL', false, 2200),
    ('platform_product.create', 'platform_product', 'create', 'Product catalogue', 'Create', 'Add products', 'FUNCTIONAL', false, 2201),
    ('platform_product.edit', 'platform_product', 'edit', 'Product catalogue', 'Edit', 'Change products and their images', 'FUNCTIONAL', false, 2202),
    ('platform_product.status', 'platform_product', 'status', 'Product catalogue', 'Activate', 'Activate and deactivate products', 'FUNCTIONAL', false, 2203),

    ('platform_order.view', 'platform_order', 'view', 'All orders', 'View', 'See every company''s orders', 'FUNCTIONAL', false, 2300),
    ('platform_order.manage', 'platform_order', 'manage', 'All orders', 'Manage', 'Assign vendors and chase recipient photos on any order', 'FUNCTIONAL', false, 2301);

-- What each old key allowed, as the new keys -- so every role, designation and user keeps
-- exactly what they could do. (campaign.manage also approved Quick Gift requests and opened the
-- Orders list; campaign.submit and campaign.approve opened Orders too.) org.scope.all is not a
-- permission any more: V46 turns its holders into whole-company users, then removes it.
CREATE TEMPORARY TABLE permission_mapping (old_key VARCHAR(150), new_key VARCHAR(150)) ON COMMIT DROP;
INSERT INTO permission_mapping (old_key, new_key) VALUES
    ('campaign.manage', 'campaign.view'), ('campaign.manage', 'campaign.create'), ('campaign.manage', 'campaign.edit'),
    ('campaign.manage', 'campaign.publish'), ('campaign.manage', 'campaign_entry.view'), ('campaign.manage', 'request.approve'),
    ('campaign.manage', 'order.view'), ('campaign.manage', 'product.view'),
    ('campaign.submit', 'campaign_entry.submit'), ('campaign.submit', 'order.view'),
    ('campaign.approve', 'campaign.view'), ('campaign.approve', 'campaign_entry.view'), ('campaign.approve', 'request.approve'),
    ('campaign.approve', 'order.view'),
    ('quickgift.create', 'request.create'), ('quickgift.create', 'product.view'), ('quickgift.create', 'recipient_list.manage'),
    ('recipient.manage', 'recipient.view'), ('recipient.manage', 'recipient.create'), ('recipient.manage', 'recipient.edit'),
    ('recipient.manage', 'recipient_list.manage'),
    ('recipient.approve', 'recipient_list.approve'),
    ('order.track', 'order.view'),
    ('order.design.review', 'order.view'), ('order.design.review', 'order.artwork_review'),
    ('order.design.final', 'order.artwork_final'),
    ('order.manage', 'platform_order.view'), ('order.manage', 'platform_order.manage'),
    ('company.manage', 'platform_company.view'), ('company.manage', 'platform_company.create'), ('company.manage', 'platform_company.edit'),
    ('company.manage', 'platform_company.status'), ('company.manage', 'platform_company.configure'),
    ('vendor.manage', 'platform_vendor.view'), ('vendor.manage', 'platform_vendor.create'), ('vendor.manage', 'platform_vendor.edit'),
    ('vendor.manage', 'platform_vendor.status'),
    ('gift.manage', 'platform_product.view'), ('gift.manage', 'platform_product.create'), ('gift.manage', 'platform_product.edit'),
    ('gift.manage', 'platform_product.status'),
    ('user.manage', 'user.view'), ('user.manage', 'user.create'), ('user.manage', 'user.edit'), ('user.manage', 'user.delete'),
    ('role.manage', 'access.view'), ('role.manage', 'access.edit'),
    ('company.vendor.manage', 'company_vendor.view'), ('company.vendor.manage', 'company_vendor.add'),
    ('warehouse.manage', 'warehouse.view'), ('warehouse.manage', 'warehouse.setup'), ('warehouse.manage', 'warehouse.operate'),
    ('menu.manage', 'menu.edit');

-- dashboard.view already has the new shape: it keeps its key and grants, and is only relabelled.
UPDATE access_controls SET module = 'dashboard', action = 'view', category = 'Dashboard', name = 'View',
    description = 'See the home dashboard for the records you can see', sort_order = 100
WHERE key = 'dashboard.view';

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT DISTINCT rac.role_id, new_ac.id
FROM role_access_controls rac
JOIN access_controls old_ac ON old_ac.id = rac.access_control_id
JOIN permission_mapping m ON m.old_key = old_ac.key
JOIN access_controls new_ac ON new_ac.key = m.new_key
ON CONFLICT DO NOTHING;

-- Platform-only permissions never go to a company designation (V24's rule).
INSERT INTO designation_access_controls (company_id, designation, access_control_id)
SELECT DISTINCT dac.company_id, dac.designation, new_ac.id
FROM designation_access_controls dac
JOIN access_controls old_ac ON old_ac.id = dac.access_control_id
JOIN permission_mapping m ON m.old_key = old_ac.key
JOIN access_controls new_ac ON new_ac.key = m.new_key AND new_ac.company_assignable
ON CONFLICT DO NOTHING;

-- A per-user override carries its effect over to each new key; if two old keys map to the same
-- new key with opposite effects, REVOKE wins (the safer reading).
INSERT INTO user_access_controls (user_id, access_control_id, effect)
SELECT uac.user_id, new_ac.id, CASE WHEN bool_or(uac.effect = 'REVOKE') THEN 'REVOKE' ELSE 'GRANT' END
FROM user_access_controls uac
JOIN access_controls old_ac ON old_ac.id = uac.access_control_id
JOIN permission_mapping m ON m.old_key = old_ac.key
JOIN access_controls new_ac ON new_ac.key = m.new_key
GROUP BY uac.user_id, new_ac.id
ON CONFLICT DO NOTHING;

-- The old keys go, and their grants with them (role/designation/user grants cascade).
DELETE FROM access_controls WHERE key IN (SELECT DISTINCT old_key FROM permission_mapping);
-- module/action become NOT NULL in V46, once org.scope.all (the last key without them) is gone.
