-- The global, platform-owned gift catalogue -- one shared catalogue every
-- company browses the active subset of. Deliberately no pricing/vendor
-- columns yet: the PRD itself flags catalogue pricing behavior as an
-- unresolved decision, and vendor-sourced pricing needs the Vendor entity,
-- which doesn't exist yet. Both can be added once those are actually designed.

CREATE TABLE gifts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL UNIQUE,
    category       VARCHAR(150) NOT NULL,
    description    TEXT,
    moq            INTEGER,
    lead_time_days INTEGER,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    created_by     UUID         NOT NULL REFERENCES users (id),
    updated_by     UUID         NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_gifts_category ON gifts (category);
CREATE INDEX idx_gifts_is_active ON gifts (is_active);

CREATE TABLE gift_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gift_id       UUID         NOT NULL REFERENCES gifts (id) ON DELETE CASCADE,
    image_key     VARCHAR(500) NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_gift_images_gift_id ON gift_images (gift_id);

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('gift.manage', 'Manage Gift Catalogue', 'Create, edit, and activate/deactivate gifts in the catalogue', 'Gift', 'FUNCTIONAL');

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT roles.id, access_controls.id
FROM roles, access_controls
WHERE roles.name = 'Super Admin' AND roles.company_id IS NULL
  AND access_controls.key = 'gift.manage';
