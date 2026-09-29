-- ===========================================================================
-- V17: the part of the tag that can actually be printed on.
--
-- A jewellery dumbbell tag is not printable end to end: it has a head, a thin
-- neck and a tail that wraps round the piece. Print placed over the neck is
-- clipped top and bottom.
--
--   content_width_mm   how much of the tag's length carries print, from the left
--   content_height_mm  how much of its height does (added in V13)
--
-- The default is the whole label: that is what the existing setup prints and
-- nothing should move by itself. A shop whose print lands on the neck sets this
-- to the width of the head in label settings, and the layout keeps inside it.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN content_width_mm NUMERIC(6,2);

UPDATE label_settings SET content_width_mm = label_width_mm WHERE content_width_mm IS NULL;

ALTER TABLE label_settings
    ALTER COLUMN content_width_mm SET NOT NULL;

ALTER TABLE label_settings
    ALTER COLUMN content_width_mm SET DEFAULT 60.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_content_width CHECK (
        content_width_mm > 0 AND content_width_mm <= label_width_mm);

COMMENT ON COLUMN label_settings.content_width_mm IS
    'Printable length of the tag, from its left edge. Anything beyond it (neck, tail) is left blank.';
