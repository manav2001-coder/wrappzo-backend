-- A product name only has to be unique among one vendor's own products: two vendors can each sell a "Prescription Pad".
-- Gifts with no vendor (platform catalogue) stay unique among themselves.
ALTER TABLE gifts DROP CONSTRAINT gifts_name_key;
CREATE UNIQUE INDEX uq_gifts_vendor_name ON gifts (COALESCE(vendor_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));
