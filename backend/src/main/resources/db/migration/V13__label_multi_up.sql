-- ===========================================================================
-- V13: labels that come several across the roll.
--
-- The shop's tags are jewellery "dumbbell" tags: a printable head with a long
-- narrow tail that wraps round the piece, five tags side by side across the
-- roll. The printer advances one ROW of five at a time, so a page has to be the
-- whole row - a page the size of one tag makes the printer feed (and waste) a
-- whole row per label.
--
--   labels_across     tags side by side across the roll; page width is
--                     label_width_mm x labels_across
--   content_height_mm how much of the tag's length is printable - the head.
--                     The rest is the tail, left blank.
--
-- The values set below are measured from a photograph of the shop's roll and
-- are a starting point: print one row, measure it, and adjust on the Label
-- settings screen.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN labels_across     SMALLINT     NOT NULL DEFAULT 1,
    ADD COLUMN content_height_mm NUMERIC(6,2) NOT NULL DEFAULT 25.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_across  CHECK (labels_across BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_label_settings_content CHECK (
        content_height_mm > 0 AND content_height_mm <= label_height_mm);

COMMENT ON COLUMN label_settings.labels_across IS
    'Tags side by side across the roll. One printed page is one row of this many.';
COMMENT ON COLUMN label_settings.content_height_mm IS
    'Printable height at the top of each tag (the head). The tail below it stays blank.';

-- Measured from the shop's own roll: five tags across, each about 21 mm wide and
-- 65 mm long, with roughly the top 22 mm printable.
UPDATE label_settings
   SET label_width_mm    = 21.00,
       label_height_mm   = 65.00,
       labels_across     = 5,
       content_height_mm = 22.00,
       margin_top_mm     = 1.50,
       margin_left_mm    = 1.50,
       barcode_height_mm = 8.00,
       barcode_module_mm = 0.200,
       serial_font_pt    = 5.50,
       purity_font_pt    = 5.00,
       shop_font_pt      = 5.50,
       updated_by        = 'system'
 WHERE id = 1;
