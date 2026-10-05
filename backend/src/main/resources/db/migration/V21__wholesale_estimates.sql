-- ===========================================================================
-- V21: wholesale estimates.
--
-- Wholesale is not priced like the counter. A retail invoice is arithmetic in
-- rupees; a wholesale estimate is arithmetic in *pure gold*, and the rupees
-- fall out of it at the day's pure rate:
--
--   pure weight = jewel weight x touch %
--   item amount = pure weight x rate + making charge + stone amount
--
-- and the party's account is carried in two currencies at once - grams of pure
-- gold, and a rupee balance for the making and stone charges. From the shop's
-- own estimate No. 3:
--
--   opening   3.373 g pure, Rs 0       -> Rs 50,460   (3.373 x 14,960)
--   one line  16.060 g at 98% = 15.739 g pure, MC Rs 160
--             15.739 x 14,960 + 160    =  Rs 2,35,615
--   closing   19.112 g pure, Rs 160    -> Rs 2,86,076
--
-- Every figure on that slip reproduces from these rules, which is why they are
-- written down here rather than guessed at in code.
--
-- No GST: the document is an estimate, not a tax invoice. The totals are kept
-- in their own columns so a tax variant can be added later without reshaping
-- what is already recorded.
-- ===========================================================================

-- --- numbering -------------------------------------------------------------
INSERT INTO document_series (code, description, prefix, separator_char, period_format, pad_width, max_length, updated_by)
VALUES ('WHOLESALE_ESTIMATE', 'Wholesale estimate', 'WE', '-', 'FINANCIAL_YEAR', 6, 16, 'system');

-- --- the running account ---------------------------------------------------
-- One row per party, created on their first estimate. A balance carried as a
-- running total rather than summed from the estimates every time: the opening
-- figures have to be read under lock while a new estimate is written, and a
-- sum over a growing table is the wrong thing to hold a lock on.
CREATE TABLE wholesale_balances (
    customer_id   BIGINT        PRIMARY KEY REFERENCES customers (id),
    pure_grams    NUMERIC(12,3) NOT NULL DEFAULT 0,
    misc_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by    VARCHAR(100),
    CONSTRAINT ck_wholesale_balance_pure CHECK (pure_grams >= 0)
);

COMMENT ON TABLE wholesale_balances IS
    'Running wholesale account per party: grams of pure gold owed, plus a rupee balance for making and stone charges.';

-- --- the estimate ----------------------------------------------------------
CREATE TABLE wholesale_estimates (
    id                      BIGSERIAL     PRIMARY KEY,
    estimate_number         VARCHAR(16)   NOT NULL,
    estimate_date           DATE          NOT NULL,
    customer_id             BIGINT        NOT NULL REFERENCES customers (id),

    -- Snapshots, so a later change to the customer record cannot rewrite a
    -- document that has already been handed over.
    customer_code           VARCHAR(20)   NOT NULL,
    customer_name           VARCHAR(150)  NOT NULL,
    customer_mobile         VARCHAR(15),

    -- The price of one gram of pure gold on the day. Everything else follows.
    pure_rate_per_gram      NUMERIC(12,2) NOT NULL,

    opening_pure_grams      NUMERIC(12,3) NOT NULL,
    opening_misc_amount     NUMERIC(14,2) NOT NULL,
    opening_value           NUMERIC(14,2) NOT NULL,

    total_pure_grams        NUMERIC(12,3) NOT NULL,
    total_misc_amount       NUMERIC(14,2) NOT NULL,
    total_amount            NUMERIC(14,2) NOT NULL,

    closing_pure_grams      NUMERIC(12,3) NOT NULL,
    closing_misc_amount     NUMERIC(14,2) NOT NULL,
    closing_value           NUMERIC(14,2) NOT NULL,

    status                  VARCHAR(16)   NOT NULL DEFAULT 'COMPLETED',
    remarks                 VARCHAR(500),
    cancel_reason           VARCHAR(500),
    cancelled_at            TIMESTAMPTZ,
    cancelled_by            VARCHAR(100),

    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by              VARCHAR(100),

    CONSTRAINT uk_wholesale_estimate_number UNIQUE (estimate_number),
    CONSTRAINT ck_wholesale_estimate_status CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_wholesale_estimate_rate   CHECK (pure_rate_per_gram > 0),
    CONSTRAINT ck_wholesale_estimate_totals CHECK (
        total_pure_grams >= 0 AND opening_pure_grams >= 0 AND closing_pure_grams >= 0)
);

CREATE INDEX ix_wholesale_estimates_customer ON wholesale_estimates (customer_id, estimate_date DESC);
CREATE INDEX ix_wholesale_estimates_date     ON wholesale_estimates (estimate_date DESC);

COMMENT ON COLUMN wholesale_estimates.pure_rate_per_gram IS
    'Rate for one gram of pure gold on the day; the whole estimate is valued from it.';
COMMENT ON COLUMN wholesale_estimates.total_misc_amount IS
    'Making charges plus stone amounts. Accrues to the party rupee balance, separately from the gold.';

-- --- the lines -------------------------------------------------------------
CREATE TABLE wholesale_estimate_items (
    id                   BIGSERIAL     PRIMARY KEY,
    estimate_id          BIGINT        NOT NULL REFERENCES wholesale_estimates (id) ON DELETE CASCADE,
    line_number          INTEGER       NOT NULL,
    inventory_item_id    BIGINT        NOT NULL REFERENCES inventory_items (id),

    -- Snapshots again: the piece is sold, and what it was called at the time
    -- is part of the document.
    serial_number        VARCHAR(6)    NOT NULL,
    jewel_name           VARCHAR(200)  NOT NULL,

    jewel_weight_grams   NUMERIC(10,3) NOT NULL,
    pure_percentage      NUMERIC(6,2)  NOT NULL,
    pure_weight_grams    NUMERIC(10,3) NOT NULL,
    rate_per_gram        NUMERIC(12,2) NOT NULL,
    making_charge        NUMERIC(12,2) NOT NULL DEFAULT 0,
    stone_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    item_amount          NUMERIC(14,2) NOT NULL,

    line_status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT uk_wholesale_item_line   UNIQUE (estimate_id, line_number),
    CONSTRAINT ck_wholesale_item_status CHECK (line_status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_wholesale_item_weight CHECK (jewel_weight_grams > 0 AND pure_weight_grams > 0),
    CONSTRAINT ck_wholesale_item_touch  CHECK (pure_percentage > 0 AND pure_percentage <= 100),
    CONSTRAINT ck_wholesale_item_rate   CHECK (rate_per_gram > 0),
    CONSTRAINT ck_wholesale_item_extras CHECK (making_charge >= 0 AND stone_amount >= 0)
);

CREATE INDEX ix_wholesale_items_estimate  ON wholesale_estimate_items (estimate_id);
CREATE INDEX ix_wholesale_items_inventory ON wholesale_estimate_items (inventory_item_id);

COMMENT ON COLUMN wholesale_estimate_items.pure_percentage IS
    'Touch, as a percentage. 916 gold is about 98 on this scale once the alloy is assayed.';
COMMENT ON COLUMN wholesale_estimate_items.pure_weight_grams IS
    'jewel_weight_grams x pure_percentage / 100, to the milligram.';

-- --- permissions -----------------------------------------------------------
INSERT INTO permissions (code, module, action, description) VALUES
    ('WHOLESALE_VIEW',          'WHOLESALE',        'VIEW',   'Open wholesale estimates and party balances'),
    ('WHOLESALE_CREATE',        'WHOLESALE',        'CREATE', 'Raise a wholesale estimate'),
    ('WHOLESALE_DELETE',        'WHOLESALE',        'DELETE', 'Cancel a wholesale estimate'),
    ('REPORT_WHOLESALE_VIEW',   'REPORT_WHOLESALE', 'VIEW',   'View the wholesale report'),
    ('REPORT_WHOLESALE_EXPORT', 'REPORT_WHOLESALE', 'EXPORT', 'Download the wholesale report as Excel');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.name = 'ROLE_ADMIN'
   AND p.module IN ('WHOLESALE', 'REPORT_WHOLESALE')
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
