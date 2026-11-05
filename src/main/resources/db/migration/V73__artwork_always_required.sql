-- Artwork approval is always part of the flow: no gift opts out. Every gift now requires an approved proof before dispatch.
UPDATE gifts SET artwork_required = true WHERE NOT artwork_required;
ALTER TABLE gifts ALTER COLUMN artwork_required SET DEFAULT true;
