-- A profile picture for people (users) and a logo for vendors: the storage key of the uploaded image, null until one is set.
ALTER TABLE users ADD COLUMN photo_key VARCHAR(500);
ALTER TABLE vendors ADD COLUMN photo_key VARCHAR(500);
