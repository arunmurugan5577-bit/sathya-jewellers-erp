-- ===========================================================================
-- V33: billing a piece that was never tagged, and the HUID that identifies it.
--
-- The small gold - a mookuthi, a nose stud, the odd thin ring - does not get
-- a serial number or a barcode tag. It is too small to carry one and too
-- quick-moving to be worth stocking individually, so it has never been in
-- inventory_items and could not be put on an invoice at all.
--
-- A sale line already carries its own copy of everything it needs to print
-- and to be taxed - the description, the HSN and its GST rate, the item type,
-- the purity, the category, the weights, the rate. Only three things stood in
-- the way: the line insisted on an inventory piece, it insisted on a serial
-- number, and there was nowhere to record the hallmark.
--
-- So inventory_item_id and serial_number become optional, and huid is added.
-- A line is now one of two things, and the check below says which:
--
--   a tagged piece  - an inventory item and its serial, as before
--   a loose piece   - neither, and whatever the counter typed
--
-- HUID is the six-character BIS hallmark stamped on the piece itself. It is
-- recorded for any line that has one, tagged or not: a hallmarked bangle out
-- of stock has one too, and a customer asking which piece they bought is
-- asking about that code.
-- ===========================================================================

ALTER TABLE sale_items ALTER COLUMN inventory_item_id DROP NOT NULL;
ALTER TABLE sale_items ALTER COLUMN serial_number     DROP NOT NULL;

ALTER TABLE sale_items ADD COLUMN huid VARCHAR(6);

-- The serial's own format check has to tolerate the absence of one now.
ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_serial;
ALTER TABLE sale_items
    ADD CONSTRAINT ck_sale_items_serial
        CHECK (serial_number IS NULL OR serial_number ~ '^[0-9]{3,6}$');

-- A piece off the shelf is identified by both or by neither. One without the
-- other would be a line nothing could trace back - or a serial pointing at a
-- piece the invoice does not claim to have taken.
ALTER TABLE sale_items
    ADD CONSTRAINT ck_sale_items_tagged_or_loose
        CHECK ((inventory_item_id IS NULL) = (serial_number IS NULL));

-- BIS stamps six alphanumeric characters, upper case.
ALTER TABLE sale_items
    ADD CONSTRAINT ck_sale_items_huid
        CHECK (huid IS NULL OR huid ~ '^[0-9A-Z]{6}$');

COMMENT ON COLUMN sale_items.inventory_item_id IS
    'The piece off the shelf. Empty for a loose piece that was never tagged.';
COMMENT ON COLUMN sale_items.huid IS
    'The six-character BIS hallmark stamped on the piece, where it carries one.';
