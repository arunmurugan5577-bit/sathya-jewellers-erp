-- The BIS hallmark stamped on a piece, where it carries one.
--
-- Optional: silver is not hallmarked, and neither is every scrap of gold the
-- shop tags. Six characters, digits and capitals, which is what BIS issues -
-- the same rule sale_items.huid is held to, so a piece and the line that
-- sells it cannot disagree about what a hallmark looks like.
ALTER TABLE inventory_items ADD COLUMN huid VARCHAR(6);

ALTER TABLE inventory_items ADD CONSTRAINT ck_inventory_items_huid
    CHECK (huid IS NULL OR huid ~ '^[0-9A-Z]{6}$');

COMMENT ON COLUMN inventory_items.huid IS
    'BIS hallmark unique identification, 6 characters. Null where the piece carries none.';
