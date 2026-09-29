-- ===========================================================================
-- V10: sales.
--
--   sales                       one tax invoice
--   sale_items                  one row per physical piece sold
--   sale_old_metal_adjustments  old gold/silver value applied to the invoice
--   sale_payments               zero or more payments (cash + UPI, etc.)
--
-- Calculation, as it appears on the shop's own tax invoice (see SaleCalculator):
--
--   gross weight  = net weight + net weight x wastage %
--   amount        = round( gross weight x rate + making charge )
--   TOTAL         = sum of amounts
--   CGST / SGST   = round( TOTAL x GST % ) split equally
--   GRAND TOTAL   = TOTAL + GST - DISCOUNT
--   payable       = GRAND TOTAL - old gold/silver adjustment
--
-- Every amount below is stored, and every relationship between them is a check
-- constraint, so a header that does not add up cannot be saved - whatever code
-- tried to save it.
-- ===========================================================================

CREATE TABLE sales (
    id                          BIGSERIAL     PRIMARY KEY,
    invoice_number              VARCHAR(16)   NOT NULL,
    customer_id                 BIGINT        NOT NULL,
    invoice_date                DATE          NOT NULL,
    status                      VARCHAR(20)   NOT NULL,
    -- snapshots: an issued invoice never changes when the customer or shop does
    customer_name               VARCHAR(150)  NOT NULL,
    customer_mobile             VARCHAR(20),
    customer_address            VARCHAR(600),
    customer_gstin              VARCHAR(15),
    seller_name                 VARCHAR(150)  NOT NULL,
    seller_address              VARCHAR(600),
    seller_mobile               VARCHAR(40),
    seller_gstin                VARCHAR(15),
    -- amounts
    subtotal                    NUMERIC(14,2) NOT NULL,
    cgst_amount                 NUMERIC(14,2) NOT NULL,
    sgst_amount                 NUMERIC(14,2) NOT NULL,
    tax_amount                  NUMERIC(14,2) NOT NULL,
    discount_amount             NUMERIC(14,2) NOT NULL DEFAULT 0,
    grand_total                 NUMERIC(14,2) NOT NULL,
    old_metal_adjustment_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    round_off_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_payable                 NUMERIC(14,2) NOT NULL,
    amount_paid                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance_amount              NUMERIC(14,2) NOT NULL,
    payment_status              VARCHAR(20)   NOT NULL,
    remarks                     VARCHAR(500),
    cancel_reason               VARCHAR(500),
    cancelled_at                TIMESTAMPTZ,
    cancelled_by                VARCHAR(100),
    version                     BIGINT        NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                  VARCHAR(100),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(100),
    CONSTRAINT uk_sales_invoice_number        UNIQUE (invoice_number),
    CONSTRAINT fk_sales_customer              FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_status                CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sales_payment_status        CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID')),
    CONSTRAINT ck_sales_non_negative          CHECK (
        subtotal >= 0 AND cgst_amount >= 0 AND sgst_amount >= 0 AND discount_amount >= 0
        AND grand_total >= 0 AND old_metal_adjustment_amount >= 0 AND net_payable >= 0
        AND amount_paid >= 0 AND balance_amount >= 0),
    CONSTRAINT ck_sales_tax_split             CHECK (tax_amount = cgst_amount + sgst_amount),
    CONSTRAINT ck_sales_grand_total           CHECK (grand_total = subtotal + tax_amount - discount_amount),
    CONSTRAINT ck_sales_adjustment_within     CHECK (old_metal_adjustment_amount <= grand_total),
    CONSTRAINT ck_sales_round_off             CHECK (round_off_amount > -1 AND round_off_amount < 1),
    CONSTRAINT ck_sales_net_payable           CHECK (net_payable = grand_total - old_metal_adjustment_amount + round_off_amount),
    CONSTRAINT ck_sales_balance               CHECK (balance_amount = net_payable - amount_paid),
    CONSTRAINT ck_sales_cancel_recorded       CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX ix_sales_invoice_date   ON sales (invoice_date);
CREATE INDEX ix_sales_customer       ON sales (customer_id);
CREATE INDEX ix_sales_status         ON sales (status);
CREATE INDEX ix_sales_payment_status ON sales (payment_status);

COMMENT ON COLUMN sales.invoice_number IS 'Backend-generated, consecutive, at most 16 characters as the GST invoice rules require.';

CREATE TABLE sale_items (
    id                   BIGSERIAL     PRIMARY KEY,
    sale_id              BIGINT        NOT NULL,
    line_number          SMALLINT      NOT NULL,
    inventory_item_id    BIGINT        NOT NULL,
    line_status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    -- snapshot of the piece as sold
    serial_number        VARCHAR(6)    NOT NULL,
    particulars          VARCHAR(200)  NOT NULL,
    hsn_code             VARCHAR(8)    NOT NULL,
    gst_percentage       NUMERIC(5,2)  NOT NULL,
    item_type_id         BIGINT        NOT NULL,
    purity_id            BIGINT        NOT NULL,
    category_id          BIGINT        NOT NULL,
    sub_category_id      BIGINT,
    -- calculation
    net_weight_grams     NUMERIC(12,3) NOT NULL,
    wastage_percentage   NUMERIC(6,2)  NOT NULL DEFAULT 0,
    wastage_weight_grams NUMERIC(12,3) NOT NULL DEFAULT 0,
    gross_weight_grams   NUMERIC(12,3) NOT NULL,
    rate_per_gram        NUMERIC(12,2) NOT NULL,
    making_charge        NUMERIC(14,2) NOT NULL DEFAULT 0,
    amount               NUMERIC(14,2) NOT NULL,
    -- invoice-level tax and discount, allocated back to the line so a future
    -- return can reverse exactly this piece's share
    cgst_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           VARCHAR(100),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by           VARCHAR(100),
    CONSTRAINT uk_sale_items_line          UNIQUE (sale_id, line_number),
    CONSTRAINT fk_sale_items_sale          FOREIGN KEY (sale_id)           REFERENCES sales (id)           ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_inventory     FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_item_type     FOREIGN KEY (item_type_id)      REFERENCES item_types (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_purity        FOREIGN KEY (purity_id)         REFERENCES purities (id)        ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_category      FOREIGN KEY (category_id)       REFERENCES categories (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_sub_category  FOREIGN KEY (sub_category_id)   REFERENCES sub_categories (id)  ON DELETE RESTRICT,
    CONSTRAINT ck_sale_items_line          CHECK (line_number > 0),
    CONSTRAINT ck_sale_items_line_status   CHECK (line_status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_sale_items_serial        CHECK (serial_number ~ '^[0-9]{6}$'),
    CONSTRAINT ck_sale_items_hsn           CHECK (hsn_code ~ '^[0-9]{4,8}$'),
    CONSTRAINT ck_sale_items_net           CHECK (net_weight_grams > 0),
    CONSTRAINT ck_sale_items_wastage       CHECK (wastage_percentage >= 0 AND wastage_percentage <= 100),
    CONSTRAINT ck_sale_items_gross         CHECK (gross_weight_grams = net_weight_grams + wastage_weight_grams),
    CONSTRAINT ck_sale_items_rate          CHECK (rate_per_gram > 0),
    CONSTRAINT ck_sale_items_amounts       CHECK (
        making_charge >= 0 AND amount >= 0 AND cgst_amount >= 0 AND sgst_amount >= 0 AND discount_amount >= 0)
);

-- The database-level guarantee that a physical piece is never on two live
-- invoices. A cancelled sale frees its lines, so the piece can be sold again.
CREATE UNIQUE INDEX uk_sale_items_active_inventory_item
    ON sale_items (inventory_item_id) WHERE line_status = 'ACTIVE';

CREATE INDEX ix_sale_items_sale      ON sale_items (sale_id);
CREATE INDEX ix_sale_items_item_type ON sale_items (item_type_id);
CREATE INDEX ix_sale_items_category  ON sale_items (category_id);
CREATE INDEX ix_sale_items_serial    ON sale_items (serial_number);

CREATE TABLE sale_old_metal_adjustments (
    id                       BIGSERIAL     PRIMARY KEY,
    sale_id                  BIGINT        NOT NULL,
    old_metal_transaction_id BIGINT        NOT NULL,
    adjustment_amount        NUMERIC(14,2) NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               VARCHAR(100),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by               VARCHAR(100),
    CONSTRAINT uk_sale_old_metal_pair        UNIQUE (sale_id, old_metal_transaction_id),
    CONSTRAINT fk_sale_old_metal_sale        FOREIGN KEY (sale_id)                  REFERENCES sales (id)                  ON DELETE RESTRICT,
    CONSTRAINT fk_sale_old_metal_transaction FOREIGN KEY (old_metal_transaction_id) REFERENCES old_metal_transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sale_old_metal_amount      CHECK (adjustment_amount > 0),
    CONSTRAINT ck_sale_old_metal_status      CHECK (status IN ('ACTIVE', 'REVERSED'))
);

CREATE INDEX ix_sale_old_metal_transaction ON sale_old_metal_adjustments (old_metal_transaction_id);

COMMENT ON TABLE sale_old_metal_adjustments IS 'Links a purchase bill to the invoice it part-paid. REVERSED when the sale is cancelled.';

CREATE TABLE sale_payments (
    id               BIGSERIAL     PRIMARY KEY,
    sale_id          BIGINT        NOT NULL,
    payment_method   VARCHAR(20)   NOT NULL,
    amount           NUMERIC(14,2) NOT NULL,
    reference_number VARCHAR(100),
    payment_date     DATE          NOT NULL,
    remarks          VARCHAR(300),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       VARCHAR(100),
    CONSTRAINT fk_sale_payments_sale   FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sale_payments_method CHECK (payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER')),
    CONSTRAINT ck_sale_payments_amount CHECK (amount > 0)
);

CREATE INDEX ix_sale_payments_sale ON sale_payments (sale_id);
CREATE INDEX ix_sale_payments_date ON sale_payments (payment_date);

-- --- Permissions -----------------------------------------------------------
INSERT INTO permissions (code, module, action, description) VALUES
    ('SALES_VIEW',   'SALES', 'VIEW',   'View and print sales invoices'),
    ('SALES_CREATE', 'SALES', 'CREATE', 'Create sales invoices'),
    ('SALES_EDIT',   'SALES', 'EDIT',   'Record further payments and edit remarks on a sale'),
    ('SALES_DELETE', 'SALES', 'DELETE', 'Cancel a sales invoice');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.module = 'SALES'
ON CONFLICT DO NOTHING;
