-- ===========================================================================
-- V16: which printer the labels go to.
--
-- Labels are now sent straight from the application to the Windows printer, so
-- there is no browser print dialog to pick the label printer in - and no chance
-- of a tag being laid out on A4 because the dialog was left on the office
-- printer. The name is the Windows printer name, e.g. "SNBC TVSE LP 46 NEO BPLE".
--
-- Empty means "use the machine's default printer".
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN printer_name VARCHAR(160);

COMMENT ON COLUMN label_settings.printer_name IS
    'Windows printer the labels are sent to. Empty uses the default printer.';
