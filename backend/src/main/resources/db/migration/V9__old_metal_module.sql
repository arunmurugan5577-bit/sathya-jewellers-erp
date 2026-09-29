-- ===========================================================================
-- V9: old gold / silver purchase.
--
-- When a customer sells old metal to the shop - on its own, or to part-pay for
-- new jewellery - it is recorded here as its own transaction with its own bill
-- (the PURCHASE BILL). A sale then *consumes* part or all of its value through
-- sale_old_metal_adjustments (V10); the amount is never just typed into the sale.
--
-- Header + lines, because one purchase bill carries several numbered rows.
--
-- Usage accounting lives on the header as used_amount. The check constraints
-- below make it impossible, at the database level, to use more than the bill is
-- worth or to leave the status out of step with the usage - even if two sales
-- race for the same old gold.
-- ===========================================================================

CREATE TABLE old_metal_transactions (
    id                 BIGSERIAL     PRIMARY KEY,
    transaction_number VARCHAR(16)   NOT NULL,
    customer_id        BIGINT        NOT NULL,
    transaction_date   DATE          NOT NULL,
    -- snapshot at issue: the printed bill must not change if the customer does
    customer_name      VARCHAR(150)  NOT NULL,
    customer_mobile    VARCHAR(20),
    customer_address   VARCHAR(600),
    seller_name        VARCHAR(150)  NOT NULL,
    seller_address     VARCHAR(600),
    seller_mobile      VARCHAR(40),
    seller_gstin       VARCHAR(15),
    total_amount       NUMERIC(14,2) NOT NULL,
    used_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL,
    remarks            VARCHAR(500),
    cancel_reason      VARCHAR(500),
    cancelled_at       TIMESTAMPTZ,
    cancelled_by       VARCHAR(100),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         VARCHAR(100),
    CONSTRAINT uk_old_metal_transaction_number UNIQUE (transaction_number),
    CONSTRAINT fk_old_metal_customer           FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_old_metal_total_positive     CHECK (total_amount > 0),
    CONSTRAINT ck_old_metal_used_within_total  CHECK (used_amount >= 0 AND used_amount <= total_amount),
    CONSTRAINT ck_old_metal_status             CHECK (status IN ('AVAILABLE', 'PARTIALLY_USED', 'USED', 'CANCELLED')),
    CONSTRAINT ck_old_metal_status_matches_usage CHECK (
           (status = 'AVAILABLE'      AND used_amount = 0)
        OR (status = 'PARTIALLY_USED' AND used_amount > 0 AND used_amount < total_amount)
        OR (status = 'USED'           AND used_amount = total_amount)
        OR (status = 'CANCELLED'      AND used_amount = 0)
    ),
    CONSTRAINT ck_old_metal_cancel_recorded CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX ix_old_metal_customer ON old_metal_transactions (customer_id);
CREATE INDEX ix_old_metal_date     ON old_metal_transactions (transaction_date);
CREATE INDEX ix_old_metal_status   ON old_metal_transactions (status);

COMMENT ON COLUMN old_metal_transactions.used_amount IS 'Value already applied to sales. available = total_amount - used_amount.';

CREATE TABLE old_metal_transaction_items (
    id                 BIGSERIAL     PRIMARY KEY,
    transaction_id     BIGINT        NOT NULL,
    line_number        SMALLINT      NOT NULL,
    item_type_id       BIGINT        NOT NULL,
    purity_id          BIGINT,
    particulars        VARCHAR(200)  NOT NULL,
    hsn_code           VARCHAR(8)    NOT NULL,
    net_weight_grams   NUMERIC(12,3) NOT NULL,
    gross_weight_grams NUMERIC(12,3),
    rate_per_gram      NUMERIC(12,2) NOT NULL,
    amount             NUMERIC(14,2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         VARCHAR(100),
    CONSTRAINT uk_old_metal_items_line      UNIQUE (transaction_id, line_number),
    CONSTRAINT fk_old_metal_items_header    FOREIGN KEY (transaction_id) REFERENCES old_metal_transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_old_metal_items_item_type FOREIGN KEY (item_type_id)   REFERENCES item_types (id)             ON DELETE RESTRICT,
    CONSTRAINT fk_old_metal_items_purity    FOREIGN KEY (purity_id)      REFERENCES purities (id)               ON DELETE RESTRICT,
    CONSTRAINT ck_old_metal_items_line      CHECK (line_number > 0),
    CONSTRAINT ck_old_metal_items_hsn       CHECK (hsn_code ~ '^[0-9]{4,8}$'),
    CONSTRAINT ck_old_metal_items_net       CHECK (net_weight_grams > 0),
    -- Gross weight is optional on the shop's purchase bill (left as "-").
    CONSTRAINT ck_old_metal_items_gross     CHECK (gross_weight_grams IS NULL OR gross_weight_grams >= net_weight_grams),
    CONSTRAINT ck_old_metal_items_rate      CHECK (rate_per_gram > 0),
    CONSTRAINT ck_old_metal_items_amount    CHECK (amount >= 0)
);

CREATE INDEX ix_old_metal_items_header    ON old_metal_transaction_items (transaction_id);
CREATE INDEX ix_old_metal_items_item_type ON old_metal_transaction_items (item_type_id);

-- --- HSN codes used on purchase bills --------------------------------------
-- The shop's purchase bill for a gold coin uses 7108 (gold, unwrought or
-- semi-manufactured). Added for convenience; skipped if already present.
INSERT INTO hsn_codes (hsn_code, description, gst_percentage, created_by, updated_by) VALUES
    ('7106', 'Silver, unwrought or semi-manufactured', 3.00, 'system', 'system'),
    ('7108', 'Gold, unwrought or semi-manufactured',   3.00, 'system', 'system')
ON CONFLICT (hsn_code) DO NOTHING;

-- --- Permissions -----------------------------------------------------------
INSERT INTO permissions (code, module, action, description) VALUES
    ('OLD_METAL_VIEW',   'OLD_METAL', 'VIEW',   'View old gold / silver purchases'),
    ('OLD_METAL_CREATE', 'OLD_METAL', 'CREATE', 'Record old gold / silver purchases'),
    ('OLD_METAL_EDIT',   'OLD_METAL', 'EDIT',   'Edit remarks on old gold / silver purchases'),
    ('OLD_METAL_DELETE', 'OLD_METAL', 'DELETE', 'Cancel an unused old gold / silver purchase');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.module = 'OLD_METAL'
ON CONFLICT DO NOTHING;
