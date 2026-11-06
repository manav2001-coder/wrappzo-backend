-- A Quick Gift / PEL order can carry several recipient photos (creatives for covers, pads and the like), not just one.
-- The single photo columns move into this table; position 1 is the first photo, the one older screens still show.
CREATE TABLE quick_gift_photos (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quick_gift_request_id UUID         NOT NULL REFERENCES quick_gift_requests (id) ON DELETE CASCADE,
    position              INTEGER      NOT NULL CHECK (position >= 1),
    photo_key             VARCHAR(500) NOT NULL,
    file_name             VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100),
    UNIQUE (quick_gift_request_id, position)
);

INSERT INTO quick_gift_photos (quick_gift_request_id, position, photo_key, file_name, content_type)
SELECT id, 1, photo_key, COALESCE(photo_file_name, 'photo'), photo_content_type
FROM quick_gift_requests
WHERE photo_key IS NOT NULL;

ALTER TABLE quick_gift_requests DROP COLUMN photo_key, DROP COLUMN photo_file_name, DROP COLUMN photo_content_type;
