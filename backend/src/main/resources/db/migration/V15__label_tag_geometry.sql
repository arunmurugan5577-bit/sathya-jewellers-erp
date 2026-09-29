-- ===========================================================================
-- V15: the shop's actual tag size.
--
-- The printable canvas of one jewellery tag is 60 x 12 mm, one tag per feed -
-- not the 21 x 65 mm, five-across guess taken from a photograph in V13. That
-- guess is what made the printer spit out four blank tags per label: a 65 mm
-- page on 12 mm media advances roughly five tags before the next gap.
--
-- 60 x 12 mm also gives room for the layout the shop asked for, side by side:
--
--      ||||||||||     WOMENS RING
--   SJ ||||||||||     GMS.20.800
--      905351 22K / 916   Size:11
--
-- Barcode: 6 digits of Code 128 at a 0.25 mm narrow bar is about 17 mm, so the
-- bars, the short name and the details block all fit across 60 mm with room to
-- spare. Height: 6.5 mm of bars plus the serial line fits inside 12 mm with a
-- 1 mm margin top and bottom.
-- ===========================================================================

UPDATE label_settings
   SET label_width_mm    = 60.00,
       label_height_mm   = 12.00,
       labels_across     = 1,
       content_height_mm = 12.00,
       margin_top_mm     = 1.00,
       margin_left_mm    = 1.50,
       offset_x_mm       = 0.00,
       offset_y_mm       = 0.00,
       barcode_height_mm = 6.50,
       barcode_module_mm = 0.250,
       serial_font_pt    = 6.50,
       purity_font_pt    = 6.00,
       shop_font_pt      = 7.00,
       detail_font_pt    = 6.00,
       updated_by        = 'system'
 WHERE id = 1;
