-- Per-vendor commercial terms, both expressed as percentages (e.g. 12.50
-- means 12.50%). Optional -- set by the Super Admin at onboarding or later,
-- not required for a vendor to function.
ALTER TABLE vendors ADD COLUMN markup_fees NUMERIC(5, 2);
ALTER TABLE vendors ADD COLUMN commission NUMERIC(5, 2);
