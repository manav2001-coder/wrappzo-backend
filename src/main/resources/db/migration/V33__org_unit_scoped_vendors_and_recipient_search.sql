-- A vendor can now be mapped to a specific org unit within a company (e.g. a vendor that
-- only serves "Zone North") instead of only company-wide. NULL keeps today's behavior --
-- visible to every org unit in the company. Scoped visibility is resolved via
-- OrgUnit.ancestorIds: a vendor mapped to org unit X is visible to any user whose own org
-- unit is X or a descendant of X.
ALTER TABLE company_vendors ADD COLUMN org_unit_id UUID REFERENCES org_units (id) ON DELETE CASCADE;

CREATE INDEX idx_company_vendors_org_unit_id ON company_vendors (org_unit_id);

-- company_vendors' PK was (company_id, vendor_id) -- that no longer holds once the same
-- vendor can be mapped twice (once company-wide, once to a specific org unit, or to two
-- different org units). Drop it in favor of a surrogate key plus a uniqueness constraint
-- that actually accounts for org_unit_id.
ALTER TABLE company_vendors DROP CONSTRAINT company_vendors_pkey;
ALTER TABLE company_vendors ADD COLUMN id UUID DEFAULT gen_random_uuid();
UPDATE company_vendors SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE company_vendors ALTER COLUMN id SET NOT NULL;
ALTER TABLE company_vendors ADD PRIMARY KEY (id);
CREATE UNIQUE INDEX uq_company_vendors_company_vendor_orgunit
    ON company_vendors (company_id, vendor_id, (COALESCE(org_unit_id, '00000000-0000-0000-0000-000000000000')));
