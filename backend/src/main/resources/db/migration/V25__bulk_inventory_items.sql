-- ===========================================================================
-- V25: a piece of stock can be a box sold by weight, not a single article.
--
-- The model so far has been one row = one article, because two 22K rings are
-- different objects with different weights. Metti (toe rings) break that: they
-- arrive as a box of a hundred-odd identical pieces, are weighed as a box, and
-- are sold by the gram - a customer takes four of them and the shop weighs what
-- they took. Numbering each one would mean a hundred tags nobody will ever read.
--
-- So a row can now be a BULK row: one serial number, one tag, and a weight that
-- goes down over many invoices instead of a status that flips once.
--
--   remaining_weight_grams  what is still in the box (or still on the shelf, for
--                           an ordinary article - then it is all or nothing)
--   bulk                    whether the row is a box or a single article
--
-- status is kept and still means what it meant: SOLD once nothing is left. The
-- two can no longer drift apart, because the check constraints below only admit
-- the combinations InventoryItem.billOut() and returnToStock() can produce.
-- ===========================================================================

ALTER TABLE inventory_items
    ADD COLUMN bulk                   BOOLEAN        NOT NULL DEFAULT FALSE,
    ADD COLUMN remaining_weight_grams NUMERIC(12, 3);

-- Existing stock is all single articles: present in full, or gone.
UPDATE inventory_items
   SET remaining_weight_grams = CASE WHEN status = 'SOLD' THEN 0 ELSE weight_grams END;

ALTER TABLE inventory_items
    ALTER COLUMN remaining_weight_grams SET NOT NULL;

ALTER TABLE inventory_items
    -- Nothing can be sold twice, and a box cannot grow.
    ADD CONSTRAINT ck_inventory_items_remaining_range
        CHECK (remaining_weight_grams >= 0 AND remaining_weight_grams <= weight_grams),
    -- SOLD means empty, and empty means SOLD. This is the invariant the sales
    -- module relies on: it reads status to decide what may be billed.
    ADD CONSTRAINT ck_inventory_items_remaining_status
        CHECK ((status = 'SOLD') = (remaining_weight_grams = 0)),
    -- A single article is never part-sold. weight_grams > 0 is already checked,
    -- so the two alternatives cannot collide.
    ADD CONSTRAINT ck_inventory_items_single_is_whole
        CHECK (bulk OR remaining_weight_grams IN (0, weight_grams));

COMMENT ON COLUMN inventory_items.bulk IS
    'TRUE when the row is a box sold by weight (metti and the like) rather than one article.';
COMMENT ON COLUMN inventory_items.remaining_weight_grams IS
    'Grams still unsold. Falls with each invoice for a bulk row; all-or-nothing for a single article.';

-- ---------------------------------------------------------------------------
-- The invoice line now records whether what it sold was a box.
--
-- uk_sale_items_active_inventory_item was the database's guarantee that a
-- physical piece is never on two live invoices. That is still exactly right for
-- a single article, and exactly wrong for a box: four customers buying metti out
-- of the same box on the same day is the ordinary case, and each of those lines
-- is live.
--
-- So the guarantee is narrowed rather than dropped. A single article keeps its
-- backstop, unchanged; a box is left to the row lock and the remaining weight,
-- which is what governs it anyway. The flag is a snapshot, like the serial
-- number and the HSN code beside it: it records what the line sold, so the
-- index does not have to reach into inventory_items to find out.
-- ---------------------------------------------------------------------------

ALTER TABLE sale_items
    ADD COLUMN bulk BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sale_items si
   SET bulk = i.bulk
  FROM inventory_items i
 WHERE i.id = si.inventory_item_id;

DROP INDEX uk_sale_items_active_inventory_item;

CREATE UNIQUE INDEX uk_sale_items_active_inventory_item
    ON sale_items (inventory_item_id) WHERE line_status = 'ACTIVE' AND NOT bulk;

COMMENT ON COLUMN sale_items.bulk IS
    'TRUE when the line sold weight out of a box rather than one whole article.';
