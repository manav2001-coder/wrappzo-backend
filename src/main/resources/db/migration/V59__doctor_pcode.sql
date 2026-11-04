-- A doctor's pcode: the unique identifier people know them by, and what they are searched for with.
-- Mandatory for every doctor added or edited from now on (enforced in the API). Doctors that exist today
-- have none yet, so the column stays nullable: they carry no pcode until someone edits or re-uploads them.
-- Unique per company, ignoring case, wherever one is set.
ALTER TABLE recipients ADD COLUMN pcode VARCHAR(50);
CREATE UNIQUE INDEX uq_recipients_company_pcode ON recipients (company_id, lower(pcode)) WHERE pcode IS NOT NULL;

-- A proposed doctor (ADD) or change (EDIT) carries it too.
ALTER TABLE doctor_change_requests ADD COLUMN proposed_pcode VARCHAR(50);
