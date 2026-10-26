-- An MR's own working list of doctors -- separate from the company-wide shared directory
-- (recipients). Removing a doctor from one MR's list only deletes this assignment row, never
-- the shared recipients row itself, which may still be on other MRs' lists.
CREATE TABLE recipient_assignments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID        NOT NULL REFERENCES recipients (id) ON DELETE CASCADE,
    mr_user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipient_assignments UNIQUE (recipient_id, mr_user_id)
);

CREATE INDEX idx_recipient_assignments_mr_user_id ON recipient_assignments (mr_user_id);
CREATE INDEX idx_recipient_assignments_recipient_id ON recipient_assignments (recipient_id);

-- An MR requesting to add a new doctor, change an existing one's details, or remove a
-- doctor from their own list -- goes to the MR's own manager (users.reports_to_id) for
-- approval before it takes effect. ADD has no recipient_id yet (the row doesn't exist
-- until approved); EDIT/REMOVE target an existing one. The proposed_* columns hold the new
-- data for ADD/EDIT; REMOVE only ever needs recipient_id.
CREATE TABLE doctor_change_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    requested_by     UUID         NOT NULL REFERENCES users (id),
    action           VARCHAR(16)  NOT NULL,
    recipient_id     UUID         REFERENCES recipients (id) ON DELETE CASCADE,
    proposed_name    VARCHAR(255),
    proposed_phone   VARCHAR(50),
    proposed_email   VARCHAR(255),
    proposed_address TEXT,
    proposed_dob     DATE,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reviewed_by      UUID         REFERENCES users (id),
    reviewed_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_doctor_change_requests_action CHECK (action IN ('ADD', 'EDIT', 'REMOVE')),
    CONSTRAINT chk_doctor_change_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_doctor_change_requests_recipient_linkage CHECK (
        (action = 'ADD' AND recipient_id IS NULL) OR (action IN ('EDIT', 'REMOVE') AND recipient_id IS NOT NULL)
    )
);

CREATE INDEX idx_doctor_change_requests_company_id ON doctor_change_requests (company_id);
CREATE INDEX idx_doctor_change_requests_requested_by ON doctor_change_requests (requested_by);
CREATE INDEX idx_doctor_change_requests_company_status ON doctor_change_requests (company_id, status);

-- Approving/rejecting a change request is "manage the recipient directory" -- same key
-- already used for recipient.manage, now also covering approval, not just raw CRUD.
