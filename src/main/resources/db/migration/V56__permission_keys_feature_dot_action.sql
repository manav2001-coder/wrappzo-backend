-- Every permission key is `feature.action`, where the feature is named the way the screen is.
--
-- The Access Control keys were `tbac.<screen>.<action>`, which made `tbac` the module and
-- `<screen>.<action>` the action; they are now `<screen>.<action>` like every other key. Opening an
-- assignment screen is `.view` and writing in it is `.assign`. A few features take the name of the
-- screen they open: Requests are Quick Gift, the Product store is the Gift store, the Recipient
-- directory and "My recipient list" are Doctors and My Doctors, and artwork review is its own
-- feature.
--
-- Keys are renamed in place, so every grant (templates, roles, designations, users, companies)
-- follows its key: nobody gains or loses access.

CREATE TEMPORARY TABLE key_rename (
    old_key      VARCHAR(150) NOT NULL,
    new_key      VARCHAR(150) NOT NULL,
    new_module   VARCHAR(64)  NOT NULL,
    new_action   VARCHAR(64)  NOT NULL,
    new_category VARCHAR(100)
);

INSERT INTO key_rename (old_key, new_key, new_module, new_action, new_category) VALUES
    ('tbac.feature_master.view', 'feature_master.view', 'feature_master', 'view', NULL),
    ('tbac.feature_master.add', 'feature_master.add', 'feature_master', 'add', NULL),
    ('tbac.feature_master.edit', 'feature_master.edit', 'feature_master', 'edit', NULL),
    ('tbac.template_master.view', 'template_master.view', 'template_master', 'view', NULL),
    ('tbac.template_master.add', 'template_master.add', 'template_master', 'add', NULL),
    ('tbac.template_master.edit', 'template_master.edit', 'template_master', 'edit', NULL),
    ('tbac.features_to_template.manage', 'features_to_template.view', 'features_to_template', 'view', NULL),
    ('tbac.features_to_template.assignment', 'features_to_template.assign', 'features_to_template', 'assign', NULL),
    ('tbac.templates_to_template.manage', 'templates_to_template.view', 'templates_to_template', 'view', NULL),
    ('tbac.templates_to_template.assignment', 'templates_to_template.assign', 'templates_to_template', 'assign', NULL),
    ('tbac.user_template.manage', 'user_template.view', 'user_template', 'view', NULL),
    ('tbac.user_template.assignment', 'user_template.assign', 'user_template', 'assign', NULL),
    ('tbac.bulk_assign_user_templates', 'user_template.bulk_assign', 'user_template', 'bulk_assign', NULL),
    ('tbac.role_template.manage', 'role_template.view', 'role_template', 'view', NULL),
    ('tbac.role_template.assignment', 'role_template.assign', 'role_template', 'assign', NULL),

    ('request.create', 'quick_gift.create', 'quick_gift', 'create', 'Quick gift'),
    ('request.approve', 'quick_gift.approve', 'quick_gift', 'approve', 'Quick gift'),
    ('product.view', 'gift_store.view', 'gift_store', 'view', 'Gift store'),
    ('order.artwork_review', 'artwork.review', 'artwork', 'review', 'Artwork'),
    ('order.artwork_final', 'artwork.final_approve', 'artwork', 'final_approve', 'Artwork'),
    ('recipient.view', 'doctor.view', 'doctor', 'view', 'Doctors'),
    ('recipient.create', 'doctor.create', 'doctor', 'create', 'Doctors'),
    ('recipient.edit', 'doctor.edit', 'doctor', 'edit', 'Doctors'),
    ('recipient_list.manage', 'my_doctors.manage', 'my_doctors', 'manage', 'My doctors'),
    ('recipient_list.approve', 'my_doctors.approve', 'my_doctors', 'approve', 'My doctors'),

    ('platform_product.view', 'platform_gift.view', 'platform_gift', 'view', 'Gift catalogue'),
    ('platform_product.create', 'platform_gift.create', 'platform_gift', 'create', 'Gift catalogue'),
    ('platform_product.edit', 'platform_gift.edit', 'platform_gift', 'edit', 'Gift catalogue'),
    ('platform_product.status', 'platform_gift.status', 'platform_gift', 'status', 'Gift catalogue');

UPDATE access_controls ac
SET key = r.new_key,
    module = r.new_module,
    action = r.new_action,
    category = COALESCE(r.new_category, ac.category),
    updated_at = now()
FROM key_rename r
WHERE ac.key = r.old_key;

-- "Open" became "View".
UPDATE access_controls SET name = replace(name, ': Open', ': View'), updated_at = now() WHERE key LIKE '%\_template.view';

-- Sending a recipient the photo-upload link from an order is an action of its own, no longer
-- something anyone who can see orders may do. Whoever holds order.view today keeps the ability.
INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('order.request_photo', 'order', 'request_photo', 'Orders', 'Request photo', 'Send a recipient the link to upload their photo for an order', 'FUNCTIONAL', true, 603);

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT g.template_id, np.id
FROM template_access_controls g
JOIN access_controls ov ON ov.id = g.access_control_id AND ov.key = 'order.view'
CROSS JOIN access_controls np WHERE np.key = 'order.request_photo'
ON CONFLICT DO NOTHING;

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT g.role_id, np.id
FROM role_access_controls g
JOIN access_controls ov ON ov.id = g.access_control_id AND ov.key = 'order.view'
CROSS JOIN access_controls np WHERE np.key = 'order.request_photo'
ON CONFLICT DO NOTHING;

INSERT INTO designation_access_controls (company_id, designation, access_control_id)
SELECT g.company_id, g.designation, np.id
FROM designation_access_controls g
JOIN access_controls ov ON ov.id = g.access_control_id AND ov.key = 'order.view'
CROSS JOIN access_controls np WHERE np.key = 'order.request_photo'
ON CONFLICT DO NOTHING;

INSERT INTO user_access_controls (user_id, access_control_id, effect, granted_by)
SELECT g.user_id, np.id, g.effect, g.granted_by
FROM user_access_controls g
JOIN access_controls ov ON ov.id = g.access_control_id AND ov.key = 'order.view'
CROSS JOIN access_controls np WHERE np.key = 'order.request_photo'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT g.company_id, np.id, g.is_active
FROM company_access_controls g
JOIN access_controls ov ON ov.id = g.access_control_id AND ov.key = 'order.view'
CROSS JOIN access_controls np WHERE np.key = 'order.request_photo'
ON CONFLICT DO NOTHING;
