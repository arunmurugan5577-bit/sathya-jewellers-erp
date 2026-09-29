-- ===========================================================================
-- V12: barcode label printing.
--
-- Two tables:
--
--   label_settings    one row, the printer calibration. Every millimetre the
--                     label layout uses lives here rather than in the code, so
--                     the shop can line the print up with its physical tags
--                     without a code change.
--   label_print_jobs  what was printed, when and by whom. A label carries the
--                     serial number that identifies a piece, so a reprint is
--                     worth a record.
--
-- Nothing here duplicates inventory: labels are generated from the existing
-- inventory_items, purities and item_types rows.
-- ===========================================================================

CREATE TABLE label_settings (
    id                     BIGINT        PRIMARY KEY,
    shop_short_name        VARCHAR(8)    NOT NULL,
    -- physical label, in millimetres
    label_width_mm         NUMERIC(6,2)  NOT NULL DEFAULT 50.00,
    label_height_mm        NUMERIC(6,2)  NOT NULL DEFAULT 25.00,
    margin_top_mm          NUMERIC(6,2)  NOT NULL DEFAULT 2.00,
    margin_left_mm         NUMERIC(6,2)  NOT NULL DEFAULT 2.00,
    offset_x_mm            NUMERIC(6,2)  NOT NULL DEFAULT 0.00,
    offset_y_mm            NUMERIC(6,2)  NOT NULL DEFAULT 0.00,
    rotation_degrees       SMALLINT      NOT NULL DEFAULT 0,
    -- barcode
    barcode_height_mm      NUMERIC(6,2)  NOT NULL DEFAULT 10.00,
    barcode_module_mm      NUMERIC(6,3)  NOT NULL DEFAULT 0.250,
    -- type sizes, in points
    serial_font_pt         NUMERIC(5,2)  NOT NULL DEFAULT 8.00,
    purity_font_pt         NUMERIC(5,2)  NOT NULL DEFAULT 7.00,
    shop_font_pt           NUMERIC(5,2)  NOT NULL DEFAULT 8.00,
    show_purity            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             VARCHAR(100),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by             VARCHAR(100),
    CONSTRAINT ck_label_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_label_settings_short_name CHECK (btrim(shop_short_name) <> ''),
    CONSTRAINT ck_label_settings_size      CHECK (
        label_width_mm BETWEEN 10 AND 200 AND label_height_mm BETWEEN 8 AND 200),
    CONSTRAINT ck_label_settings_margins   CHECK (
        margin_top_mm >= 0 AND margin_left_mm >= 0
        AND margin_top_mm < label_height_mm AND margin_left_mm < label_width_mm),
    CONSTRAINT ck_label_settings_offsets   CHECK (
        offset_x_mm BETWEEN -20 AND 20 AND offset_y_mm BETWEEN -20 AND 20),
    CONSTRAINT ck_label_settings_rotation  CHECK (rotation_degrees IN (0, 90, 180, 270)),
    CONSTRAINT ck_label_settings_barcode   CHECK (
        barcode_height_mm BETWEEN 3 AND 100 AND barcode_module_mm BETWEEN 0.10 AND 1.00),
    CONSTRAINT ck_label_settings_fonts     CHECK (
        serial_font_pt BETWEEN 3 AND 30 AND purity_font_pt BETWEEN 3 AND 30
        AND shop_font_pt BETWEEN 3 AND 30)
);

COMMENT ON TABLE label_settings IS 'Printer calibration for barcode labels. One row, id = 1.';
COMMENT ON COLUMN label_settings.barcode_module_mm IS
    'Width of the narrowest bar. 0.25 mm suits a 203 dpi thermal printer such as the TVS LP 46 NEO.';
COMMENT ON COLUMN label_settings.offset_x_mm IS
    'Shifts the whole label, for when the printed image sits off the physical tag.';

-- The shop short name is a placeholder until the shop sets its own; every other
-- value is a sensible starting point for a 50 x 25 mm tag.
INSERT INTO label_settings (id, shop_short_name, created_by, updated_by)
VALUES (1, 'SJ', 'system', 'system');

CREATE TABLE label_print_jobs (
    id             BIGSERIAL     PRIMARY KEY,
    printed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    printed_by     VARCHAR(100)  NOT NULL,
    label_count    INTEGER       NOT NULL,
    serial_numbers TEXT          NOT NULL,
    CONSTRAINT ck_label_print_jobs_count CHECK (label_count > 0)
);

CREATE INDEX ix_label_print_jobs_printed_at ON label_print_jobs (printed_at);

COMMENT ON TABLE label_print_jobs IS 'One row per print run: who printed which serial numbers, and when.';

-- --- Permissions -----------------------------------------------------------
-- VIEW   = open the screen and preview labels
-- CREATE = send labels to the printer
-- EDIT   = change the short name and the printer calibration
INSERT INTO permissions (code, module, action, description) VALUES
    ('LABEL_VIEW',   'LABEL', 'VIEW',   'Open label printing and preview labels'),
    ('LABEL_CREATE', 'LABEL', 'CREATE', 'Print barcode labels'),
    ('LABEL_EDIT',   'LABEL', 'EDIT',   'Change label printing settings');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.module = 'LABEL'
ON CONFLICT DO NOTHING;
