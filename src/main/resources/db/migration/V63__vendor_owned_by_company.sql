-- A vendor belongs to the company that added it; the company admin runs it, and platform staff only view it.
ALTER TABLE vendors ADD COLUMN company_id UUID REFERENCES companies (id);
CREATE INDEX idx_vendors_company ON vendors (company_id);

-- Vendors Wrappzo added earlier belong to the one company they serve. One that serves several (or none) stays unowned.
UPDATE vendors v
SET company_id = x.company_id
FROM (
    SELECT vendor_id, MIN(company_id::text)::uuid AS company_id
    FROM company_vendors
    GROUP BY vendor_id
    HAVING COUNT(DISTINCT company_id) = 1
) x
WHERE v.id = x.vendor_id;

-- Platform staff can no longer add, edit, suspend or map vendors; their keys for it go with that.
DELETE FROM access_controls WHERE key IN ('platform_vendor.create', 'platform_vendor.edit', 'platform_vendor.status');
