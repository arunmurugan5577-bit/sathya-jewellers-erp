-- ===========================================================================
-- V32: where the tag's own split line falls.
--
-- The shop's tags are die-cut: a vertical line divides the head into a
-- barcode section and a description section, and the two are meant to read
-- as separate panels. Until now the layout assumed that line sat at the
-- middle of the tag, because that is what the shop described it as:
--
--     SJ |||||||||||  |  VALAIKAPPU BANGLE
--     TKS   2775      |  GMS.10.000
--                     ^ the split
--
-- On the tag in front of them it does not. The bars ran up to and across the
-- line, and the description started some way past it, because both were
-- measured from a middle that is not where the cut is.
--
-- The position cannot be worked out from anything the application knows - it
-- is a property of the die that cut the roll - so it is stored, like every
-- other measurement of this tag. Null means the middle, which is what the
-- layout did before this migration, so no tag changes until the shop measures
-- its own stock and enters it.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN split_line_mm NUMERIC(6, 2);

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_split_line
        CHECK (split_line_mm IS NULL OR (split_line_mm > 0 AND split_line_mm < label_width_mm));

COMMENT ON COLUMN label_settings.split_line_mm IS
    'Distance from the tag''s left edge to its die-cut split line. Empty means the middle of the tag.';
