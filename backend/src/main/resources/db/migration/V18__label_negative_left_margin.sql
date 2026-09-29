-- ===========================================================================
-- V18: let the left margin go negative.
--
-- The shop's printer driver lays the page down some way into the tag, so the
-- print starts well past the leading edge however the layout is set up. A
-- negative left margin drags the artwork back the other way, which is the only
-- adjustment available from inside the application; the printer grows the page
-- to the left to match, so nothing is clipped.
--
-- The top margin stays at zero or more: there is no equivalent problem down the
-- tag, and a negative one would only push print off the edge.
-- ===========================================================================

ALTER TABLE label_settings
    DROP CONSTRAINT ck_label_settings_margins;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_margins CHECK (
        margin_top_mm >= 0 AND margin_top_mm < label_height_mm
        AND margin_left_mm > -label_width_mm AND margin_left_mm < label_width_mm);

COMMENT ON COLUMN label_settings.margin_left_mm IS
    'Where the artwork starts along the tag. Negative pulls it back towards the leading edge.';
