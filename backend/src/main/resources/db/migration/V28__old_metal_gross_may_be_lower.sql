-- ===========================================================================
-- V28: old gold's two weights are not ordered.
--
-- V9 assumed what the words usually mean - gross is everything on the scale,
-- net is what is left after the stones come out - and so required
-- gross >= net. The shop does not use them that way. It writes down what the
-- piece weighed as it came in, and then the weight the two parties settle on
-- after a deduction for solder and wear, and that second figure is the smaller
-- one. It is also the one the bill is paid on: the amount has been gross x rate
-- since the shop corrected it.
--
-- So the ordering goes. Both weights must still be real weights - positive, and
-- the check on net is untouched - but which of them is larger is the shop's
-- business and not a rule the database can state.
-- ===========================================================================

ALTER TABLE old_metal_transaction_items
    DROP CONSTRAINT ck_old_metal_items_gross,
    ADD CONSTRAINT ck_old_metal_items_gross
        CHECK (gross_weight_grams IS NULL OR gross_weight_grams > 0);

COMMENT ON COLUMN old_metal_transaction_items.gross_weight_grams IS
    'The weight the bill is paid on. May be under the net weight: the shop deducts for solder and wear.';
