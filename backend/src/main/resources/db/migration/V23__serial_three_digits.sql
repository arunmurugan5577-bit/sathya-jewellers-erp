-- ===========================================================================
-- V23: serial numbers are three digits, and grow.
--
-- Six digits was a guess that made the shop's first tag read 000001. It wants
-- 001. So the number is padded to three and no further: 001 ... 999, then
-- 1000, 1001, out to 999999, which is as far as the column goes.
--
-- Padding is presentation, not identity. A tag printed 001 and a tag printed
-- 000001 would be the same piece, which is exactly the confusion the padding
-- exists to prevent - so the width is fixed in one place (the series row) and
-- everything that reads a serial normalises through it.
--
-- Tags already printed with six digits keep their numbers: 905351 pads to
-- itself, and nothing in the range below 100 is in use.
-- ===========================================================================

UPDATE document_series
   SET pad_width = 3, updated_by = 'system'
 WHERE code = 'INVENTORY_SERIAL';

-- The stored format widens to let a three digit number in. Still at most six,
-- because that is the column and the counter's ceiling.
ALTER TABLE inventory_items DROP CONSTRAINT ck_inventory_items_serial;
ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_serial CHECK (serial_number ~ '^[0-9]{3,6}$');

ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_serial;
ALTER TABLE sale_items
    ADD CONSTRAINT ck_sale_items_serial CHECK (serial_number ~ '^[0-9]{3,6}$');

COMMENT ON COLUMN inventory_items.serial_number IS
    'Three to six digits, zero padded to three. Issued by the INVENTORY_SERIAL counter and printed as the barcode.';
