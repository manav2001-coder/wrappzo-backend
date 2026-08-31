-- A company-wide recipient directory (PRD-adjacent, user-requested) -- distinct from
-- CampaignRecipientCategory (an audience-type enum) and RecipientUploadToken (a photo
-- self-upload link). This is a real roster: every recipient captured via Quick Gift or a
-- Campaign submission is deduped in here by name+phone+email so the company builds up one
-- shared address book instead of a fresh one-off per request/submission.
CREATE TABLE recipients (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    phone         VARCHAR(50)  NOT NULL,
    email         VARCHAR(255),
    address       TEXT,
    date_of_birth DATE,
    created_by    UUID         REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_recipients_company_id ON recipients (company_id);
CREATE INDEX idx_recipients_company_id_dob ON recipients (company_id, date_of_birth);

-- Quick Gift requests capture the same recipient fields the new directory does -- carry
-- date of birth on the request itself too (denormalized, same convention as recipient_name/
-- recipient_phone/recipient_email already living directly on this table).
ALTER TABLE quick_gift_requests ADD COLUMN recipient_dob DATE;

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('recipient.manage', 'Manage Recipients', 'View the shared recipient directory and add new recipients to it', 'Recipients', 'FUNCTIONAL');

-- Every company role (Company Admin and Member alike) can see and add to the shared
-- recipient directory -- same both-roles grant pattern as quickgift.create/dashboard.view (V25).
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT r.id, ac.id
FROM roles r
CROSS JOIN access_controls ac
WHERE r.company_id IS NOT NULL
  AND ac.key = 'recipient.manage'
  AND NOT EXISTS (
      SELECT 1 FROM role_access_controls rac
      WHERE rac.role_id = r.id AND rac.access_control_id = ac.id
  );

-- Date of Birth joins the default field set (DefaultCampaignFields) for every *new*
-- campaign going forward -- backfill it onto every existing campaign's form too, appended
-- after that form's current last field, so it's available as an option on every submission
-- form without anyone having to add it by hand.
INSERT INTO campaign_fields (campaign_form_id, field_key, label, field_type, required, placeholder, options, display_order)
SELECT cf.id, 'recipientDob', 'Date of Birth', 'DATE', false, NULL, '{}', COALESCE(existing.max_order, -1) + 1
FROM campaign_forms cf
LEFT JOIN (
    SELECT campaign_form_id, MAX(display_order) AS max_order
    FROM campaign_fields
    GROUP BY campaign_form_id
) existing ON existing.campaign_form_id = cf.id
WHERE NOT EXISTS (
    SELECT 1 FROM campaign_fields f WHERE f.campaign_form_id = cf.id AND f.field_key = 'recipientDob'
);
