-- Which vendors serve which company -- the Gift Store / Quick Gift catalog (GiftBrowseController)
-- is scoped to "active gifts whose vendor is in the viewer's company's roster" instead of
-- every active gift platform-wide. Many-to-many: a vendor can serve more than one company,
-- and a company can have more than one vendor.
CREATE TABLE company_vendors (
    company_id UUID        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    vendor_id  UUID        NOT NULL REFERENCES vendors (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, vendor_id)
);

CREATE INDEX idx_company_vendors_vendor_id ON company_vendors (vendor_id);

-- Backfill so this doesn't silently empty out every existing company's Gift Store the
-- moment it ships: every company gets every vendor that currently has at least one
-- active gift. A platform admin narrows this per company afterward via the new
-- Vendors section on the company's admin page.
INSERT INTO company_vendors (company_id, vendor_id)
SELECT DISTINCT c.id, g.vendor_id
FROM companies c
CROSS JOIN gifts g
WHERE g.vendor_id IS NOT NULL AND g.is_active = true;
