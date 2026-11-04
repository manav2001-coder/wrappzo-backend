-- A doctor's pcode: the unique identifier people know them by, and what they are searched for with. It REPLACES the
-- registration number, which was in practice the same code (the client's sheets called it PCODE): every stored
-- registration number becomes that doctor's pcode, and the registration number is gone from the schema.
--
-- No doctor may exist without a pcode. The few that had no registration number (typed in by hand, or stray
-- duplicates) get a visibly dummy one -- DUMMY-<8 hex of the id>, unique by construction -- to be replaced by the
-- real code when someone edits the doctor or uploads a sheet.

ALTER TABLE recipients ADD COLUMN pcode VARCHAR(100);
UPDATE recipients SET pcode = btrim(registration_number) WHERE registration_number IS NOT NULL AND btrim(registration_number) <> '';
UPDATE recipients SET pcode = 'DUMMY-' || upper(substr(replace(id::text, '-', ''), 1, 8)) WHERE pcode IS NULL;
ALTER TABLE recipients ALTER COLUMN pcode SET NOT NULL;
CREATE UNIQUE INDEX uq_recipients_company_pcode ON recipients (company_id, lower(pcode));
DROP INDEX uq_recipients_company_registration_number;
ALTER TABLE recipients DROP COLUMN registration_number;

-- A proposed doctor (ADD) or change (EDIT) carries a pcode in its place.
ALTER TABLE doctor_change_requests ADD COLUMN proposed_pcode VARCHAR(100);
UPDATE doctor_change_requests SET proposed_pcode = btrim(proposed_registration_number)
WHERE proposed_registration_number IS NOT NULL AND btrim(proposed_registration_number) <> '';
ALTER TABLE doctor_change_requests DROP COLUMN proposed_registration_number;

-- What an order's vendor prints on the pad, and what a monthly plan re-sends: the pcode, as it was when ordered.
ALTER TABLE quick_gift_requests RENAME COLUMN recipient_registration_number TO recipient_pcode;
ALTER TABLE recurring_order_plans RENAME COLUMN recipient_registration_number TO recipient_pcode;
