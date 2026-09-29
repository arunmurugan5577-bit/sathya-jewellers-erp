-- ===========================================================================
-- V19: the shop's mark on the tag - its short name, or its logo.
--
-- "SJ" beside the barcode is legible but plain. The shop logo reads well even
-- at this size: at 203 dpi a 6.5 mm box is 52 dots, enough for the monogram to
-- come out as white knocked from a dark disc.
--
--   shop_mark            LOGO (default), TEXT for the short name, NONE for neither
--   shop_logo_height_mm  the box the logo is drawn in; it is square
--
-- The short name stays required either way: it is the fallback if the logo
-- cannot be loaded, and switching back to TEXT must not need a second edit.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN shop_mark VARCHAR(10) NOT NULL DEFAULT 'LOGO';

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_shop_mark CHECK (shop_mark IN ('TEXT', 'LOGO', 'NONE'));

ALTER TABLE label_settings
    ADD COLUMN shop_logo_height_mm NUMERIC(5,2) NOT NULL DEFAULT 6.50;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_logo_height CHECK (
        shop_logo_height_mm >= 2 AND shop_logo_height_mm <= 40);

COMMENT ON COLUMN label_settings.shop_mark IS
    'What is printed beside the barcode: the shop LOGO, its short name as TEXT, or NONE.';
COMMENT ON COLUMN label_settings.shop_logo_height_mm IS
    'Height of the square box the logo is drawn in. Below about 4 mm the monogram stops reading.';
