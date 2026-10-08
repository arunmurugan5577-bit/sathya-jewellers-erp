-- ===========================================================================
-- V22: serial numbers are issued by the application, not typed.
--
-- A serial number identifies one physical piece and is printed on its tag as a
-- barcode. Typing it invites the two mistakes that matter most: a duplicate,
-- which puts the same barcode on two pieces, and a gap, which makes a stock
-- count look wrong. So the counter issues them, one after another.
--
-- It reuses the document numbering already in place for invoices: the counter
-- row is locked and incremented inside the caller's transaction, so a piece
-- that fails to save gives its number back rather than leaving a hole.
--
-- Format: six digits, no prefix and no period - 000001, 000002, ... - which is
-- what the existing tags carry and what the barcode encodes.
-- ===========================================================================

INSERT INTO document_series (code, description, prefix, separator_char, period_format, pad_width, max_length, updated_by)
VALUES ('INVENTORY_SERIAL', 'Inventory piece serial number', '', '', 'NONE', 6, 6, 'system');

-- Starts at 1 so the first piece is 000001. The shop can move it: see the
-- permission below.
INSERT INTO document_counters (series_code, period_key, next_value)
VALUES ('INVENTORY_SERIAL', 'ALL', 1)
ON CONFLICT (series_code, period_key) DO NOTHING;

-- --- who may move the counter ---------------------------------------------
-- Its own module rather than INVENTORY_EDIT: changing where the numbering
-- starts affects every tag printed afterwards, which is not the same kind of
-- decision as correcting a weight. Granted to administrators only.
INSERT INTO permissions (code, module, action, description) VALUES
    ('INVENTORY_SERIAL_EDIT', 'INVENTORY_SERIAL', 'EDIT', 'Set where inventory serial numbers start');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.name = 'ROLE_ADMIN'
   AND p.code = 'INVENTORY_SERIAL_EDIT'
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

COMMENT ON TABLE document_counters IS
    'Next number per series and period. period_key is ALL for series that never reset. '
    'INVENTORY_SERIAL is where inventory serial numbers are issued from.';
