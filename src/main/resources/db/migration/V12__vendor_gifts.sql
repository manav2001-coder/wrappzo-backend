-- Vendors can now submit their own gifts directly to the catalogue -- visible
-- immediately (is_active defaults true), just like an admin-authored gift.
-- Super Admin can view and delist (deactivate) a vendor's gift but not edit
-- its content, enforced in GiftService. created_by/updated_by become
-- nullable: a vendor-submitted gift has no admin author until (if ever) an
-- admin actually touches it via activate/deactivate.

ALTER TABLE gifts ADD COLUMN vendor_id UUID REFERENCES vendors (id);
CREATE INDEX idx_gifts_vendor_id ON gifts (vendor_id);

ALTER TABLE gifts ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE gifts ALTER COLUMN updated_by DROP NOT NULL;
