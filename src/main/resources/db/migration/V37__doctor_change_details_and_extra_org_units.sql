-- 1. A doctor change request carries the prescription-pad print details too (V36 added them to
--    the directory itself), so an MR adding or correcting a doctor can include them.
ALTER TABLE doctor_change_requests ADD COLUMN proposed_qualification       VARCHAR(255);
ALTER TABLE doctor_change_requests ADD COLUMN proposed_registration_number VARCHAR(100);
ALTER TABLE doctor_change_requests ADD COLUMN proposed_speciality          VARCHAR(255);
ALTER TABLE doctor_change_requests ADD COLUMN proposed_clinic_name         VARCHAR(255);

-- 2. Org units a user covers beyond their own (users.org_unit_id) -- a zonal manager looking
--    after two zones. Widens what they see (OrgScope); their own unit is still what their
--    orders and requests are stamped with.
CREATE TABLE user_extra_org_units (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_unit_id UUID        NOT NULL REFERENCES org_units (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, org_unit_id)
);

CREATE INDEX idx_user_extra_org_units_org_unit_id ON user_extra_org_units (org_unit_id);
