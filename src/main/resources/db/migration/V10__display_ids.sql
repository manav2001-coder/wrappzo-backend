-- Human-friendly sequential display numbers ("CID-1", "VID-1", "GID-1" in the
-- UI), layered on top of the real UUID primary keys. UUIDs stay exactly as
-- they are everywhere that matters (foreign keys, URLs, JWT claims) -- these
-- columns exist purely for people to read and say out loud. Identity columns
-- backfill existing rows automatically when added to an already-populated
-- table, in row order.
ALTER TABLE companies ADD COLUMN company_no INTEGER GENERATED ALWAYS AS IDENTITY;
ALTER TABLE vendors ADD COLUMN vendor_no INTEGER GENERATED ALWAYS AS IDENTITY;
ALTER TABLE gifts ADD COLUMN gift_no INTEGER GENERATED ALWAYS AS IDENTITY;
