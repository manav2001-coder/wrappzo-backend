-- A campaign can offer a choice of gifts (e.g. "pick a hamper or a plant"), not just one --
-- and picking at least one is now mandatory at creation (enforced in CampaignService, not
-- a DB constraint, since "at least one" doesn't map to a plain NOT NULL on a join table).
CREATE TABLE campaign_gifts (
    campaign_id UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    gift_id     UUID NOT NULL REFERENCES gifts (id),
    PRIMARY KEY (campaign_id, gift_id)
);

CREATE INDEX idx_campaign_gifts_gift_id ON campaign_gifts (gift_id);

-- Each recipient may get a different gift from the campaign's list -- the chosen one is
-- captured on the submission itself, not assumed from the campaign.
ALTER TABLE submissions ADD COLUMN gift_id UUID REFERENCES gifts (id);

-- Carry forward any existing single-gift campaigns/submissions before dropping the old column.
INSERT INTO campaign_gifts (campaign_id, gift_id)
SELECT id, gift_id FROM campaigns WHERE gift_id IS NOT NULL;

UPDATE submissions s SET gift_id = c.gift_id
FROM campaigns c WHERE s.campaign_id = c.id AND c.gift_id IS NOT NULL;

ALTER TABLE campaigns DROP COLUMN gift_id;
