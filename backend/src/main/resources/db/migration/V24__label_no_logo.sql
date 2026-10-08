-- ===========================================================================
-- V24: the tag carries the shop's short name, not its logo.
--
-- The logo reads at 203 dpi, but it is a solid dark square on a tag only 12 mm
-- tall, and the shop does not want it there. The short name is what a jeweller
-- actually needs beside the barcode: it says whose stock the piece is when a
-- tag turns up loose in a tray.
--
-- LOGO stays a choice on the settings screen - the artwork, the layout and the
-- thresholding are all still in place - so turning it back on is one dropdown
-- and no migration. Only the default changes, and this shop's current setting.
-- ===========================================================================

ALTER TABLE label_settings
    ALTER COLUMN shop_mark SET DEFAULT 'TEXT';

UPDATE label_settings
   SET shop_mark  = 'TEXT',
       updated_by = 'system'
 WHERE id = 1;

COMMENT ON COLUMN label_settings.shop_mark IS
    'What is printed beside the barcode: the shop short name as TEXT (default), its LOGO, or NONE.';
