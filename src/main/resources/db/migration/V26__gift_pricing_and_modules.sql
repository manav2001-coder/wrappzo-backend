-- Gift pricing -- price is the base cost; markup and commission are percentages layered
-- on top to compute the customer-facing Total Price (price + price*markup% + price*commission%).
-- Both fee percentages default to 0 so an unpriced-for-fees gift's total price is just its
-- base price. price itself stays nullable -- existing gifts predate this feature and show
-- no price until an admin/vendor sets one.
ALTER TABLE gifts ADD COLUMN price NUMERIC(10, 2);
ALTER TABLE gifts ADD COLUMN markup_fee_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0;
ALTER TABLE gifts ADD COLUMN commission_fee_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0;

-- Snapshotted onto the Order at approval time (from the gift's total price then) so a
-- later price change never retroactively changes an already-placed order's billed amount.
ALTER TABLE orders ADD COLUMN total_price NUMERIC(10, 2);

-- Every gift is now available in every module unconditionally -- no more per-gift module
-- picker. Backfill existing gifts (including vendor-submitted ones, which started with none
-- set) onto all three so nothing already in the catalogue silently disappears from
-- Campaigns/Quick Gift/Bulk Gifting pickers.
INSERT INTO gift_modules (gift_id, module)
SELECT g.id, m.module
FROM gifts g
CROSS JOIN (VALUES ('QUICK_GIFT'), ('BULK_GIFTING'), ('CAMPAIGN')) AS m(module)
ON CONFLICT (gift_id, module) DO NOTHING;
