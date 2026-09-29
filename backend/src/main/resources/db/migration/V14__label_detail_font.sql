-- ===========================================================================
-- V14: type size for the item details printed beside the barcode.
--
-- The label now carries the piece's name, weight and size in a block to the
-- right of the barcode (KOLUSU / GMS.32.130 / Size:123456), which needs a size
-- of its own - it is usually set smaller than the serial number.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN detail_font_pt NUMERIC(5,2) NOT NULL DEFAULT 6.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_detail_font CHECK (detail_font_pt BETWEEN 3 AND 30);

COMMENT ON COLUMN label_settings.detail_font_pt IS
    'Type size for the item name, weight and size printed beside the barcode.';
