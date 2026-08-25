-- Which commerce paths a gift is eligible to be picked from (Quick Gift / Bulk Gifting /
-- Campaign) -- admin-controlled, per PRD Part 2.1. A gift may belong to more than one,
-- so this is a join table rather than a single column.
CREATE TABLE gift_modules (
    gift_id UUID        NOT NULL REFERENCES gifts (id) ON DELETE CASCADE,
    module  VARCHAR(32) NOT NULL,
    PRIMARY KEY (gift_id, module),
    CONSTRAINT chk_gift_modules_module CHECK (module IN ('QUICK_GIFT', 'BULK_GIFTING', 'CAMPAIGN'))
);

CREATE INDEX idx_gift_modules_module ON gift_modules (module);
