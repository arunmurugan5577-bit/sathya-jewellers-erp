-- ===========================================================================
-- V27: a second mark, printed standing up before the barcode.
--
-- The tag's head used to read  SJ |||||||||  BANGLE. The shop wants the short
-- name over the description instead, where there is room for it, and the strip
-- in front of the bars given to a second mark set on its side:
--
--     T                SJ
--     K  |||||||||||   BANGLE
--     S  001 24K/999   GMS.92.500
--
-- It is a column of its own rather than more text in the existing one because
-- the strip before the bars is about 3 mm wide and nothing reads across it.
-- Turned on its side, three characters fit comfortably in the 12 mm height.
--
-- Stored rather than hardcoded: the shop has changed its mind about what sits
-- on the tag several times, and the short name beside it has been editable
-- since the first version.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN side_mark_text VARCHAR(8) DEFAULT 'TKS';

-- Empty is a real choice: it means nothing is printed in the strip and the
-- barcode starts at the left margin, as it did before this migration.
COMMENT ON COLUMN label_settings.side_mark_text IS
    'Printed on its side before the barcode. Empty for nothing.';

UPDATE label_settings
   SET side_mark_text = 'TKS',
       updated_by     = 'system'
 WHERE id = 1;
