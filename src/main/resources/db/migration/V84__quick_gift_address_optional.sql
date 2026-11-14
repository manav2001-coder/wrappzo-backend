-- The delivery address is no longer asked for when an order is requested: the vendor ships to the company's warehouse or
-- the requester, so an order can carry no address of its own.
ALTER TABLE quick_gift_requests ALTER COLUMN delivery_address DROP NOT NULL;
