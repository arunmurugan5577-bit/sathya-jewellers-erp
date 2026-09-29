-- ===========================================================================
-- V8: inventory availability.
--
-- Until now an inventory row only had `active`, an administrative on/off flag.
-- Selling needs a separate fact - has this physical piece left the shop? - so
-- that the same piece can never be invoiced twice. The two are independent:
--
--     sellable  =  status = 'AVAILABLE'  AND  active
--
-- Existing rows default to AVAILABLE, which is true of all current stock.
-- This is deliberately not the future inventory-movement ledger; it is the
-- single fact that selling requires.
-- ===========================================================================

ALTER TABLE inventory_items
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_status CHECK (status IN ('AVAILABLE', 'SOLD'));

CREATE INDEX ix_inventory_items_status ON inventory_items (status);

COMMENT ON COLUMN inventory_items.status IS 'AVAILABLE until invoiced; SOLD afterwards. Returns will add further states.';

-- The shop's tax invoice lists this figure as the NET weight - wastage is added
-- on top at billing to reach the gross (chargeable) weight. The original comment
-- called it gross; corrected here.
COMMENT ON COLUMN inventory_items.weight_grams IS 'Net weight of the piece in grams, 3 decimal places. Gross weight is computed at sale time as net + wastage.';
