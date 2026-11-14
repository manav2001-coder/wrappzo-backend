-- Dates of birth are no longer part of the product: not asked for, not shown, no birthday reminders.
-- The old campaign "Date of Birth" field goes with them (its submitted values first -- they are RESTRICTed).
DELETE FROM submission_values WHERE campaign_field_id IN (SELECT id FROM campaign_fields WHERE field_key = 'recipientDob');
DELETE FROM campaign_fields WHERE field_key = 'recipientDob';
DROP INDEX IF EXISTS idx_recipients_company_id_dob;
ALTER TABLE recipients DROP COLUMN IF EXISTS date_of_birth;
ALTER TABLE quick_gift_requests DROP COLUMN IF EXISTS recipient_dob;
