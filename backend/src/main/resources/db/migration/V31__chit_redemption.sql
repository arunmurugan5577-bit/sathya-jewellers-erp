-- ===========================================================================
-- V31: spending a matured chit.
--
-- Two ways out, and the shop treats them as the same event:
--
--   CASH  the customer takes the money. For a gold chit that is the grams at
--         the rate on the day they ask for it, which is the point of the
--         scheme - the gold was bought at twelve different rates and is sold
--         at one.
--   SALE  the customer buys jewellery. The chit comes off an ordinary tax
--         invoice, the way old gold already does: GST is charged on the goods
--         in full and the chit reduces what is payable, so there is one
--         invoice series and one set of books.
--
-- Both are rows in chit_redemptions, so "what became of this chit" is one
-- query whichever way it went, and a chit can be spent over more than one
-- visit - 24,000 of chit against an 18,000 invoice leaves 6,000 on the chit.
--
-- A redemption against a cancelled or deleted invoice is released, not
-- erased: the value goes back on the chit and the row is marked CANCELLED,
-- which is what sale_old_metal_adjustments already does.
-- ===========================================================================

CREATE TABLE chit_redemptions (
    id              BIGSERIAL      PRIMARY KEY,
    chit_account_id BIGINT         NOT NULL,
    redeemed_on     DATE           NOT NULL,
    kind            VARCHAR(10)    NOT NULL,
    -- What came off the chit. A money chit spends rupees and carries no
    -- grams; a gold chit spends grams, valued at the rate on the day.
    grams           NUMERIC(12, 3),
    rate_per_gram   NUMERIC(14, 2),
    amount          NUMERIC(14, 2) NOT NULL,
    -- Set when kind = 'SALE'. Deleting an invoice takes its redemptions with
    -- it; the value is put back on the chit first.
    sale_id         BIGINT,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    payment_method  VARCHAR(20),
    reference_no    VARCHAR(100),
    remarks         VARCHAR(300),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_chit_redemptions_account FOREIGN KEY (chit_account_id) REFERENCES chit_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_chit_redemptions_sale    FOREIGN KEY (sale_id)         REFERENCES sales (id) ON DELETE CASCADE,
    CONSTRAINT ck_chit_redemptions_kind    CHECK (kind IN ('CASH', 'SALE')),
    CONSTRAINT ck_chit_redemptions_status  CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_chit_redemptions_amount  CHECK (amount > 0),
    -- Grams and the rate they were valued at travel together or not at all.
    CONSTRAINT ck_chit_redemptions_gold    CHECK ((grams IS NULL) = (rate_per_gram IS NULL)),
    CONSTRAINT ck_chit_redemptions_grams   CHECK (grams IS NULL OR grams > 0),
    CONSTRAINT ck_chit_redemptions_rate    CHECK (rate_per_gram IS NULL OR rate_per_gram > 0),
    -- An invoice redemption names its invoice; a cash one never does.
    CONSTRAINT ck_chit_redemptions_sale    CHECK ((kind = 'SALE') = (sale_id IS NOT NULL)),
    CONSTRAINT ck_chit_redemptions_method  CHECK (payment_method IS NULL
                                                  OR payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER'))
);
CREATE INDEX ix_chit_redemptions_account ON chit_redemptions (chit_account_id);
CREATE INDEX ix_chit_redemptions_sale    ON chit_redemptions (sale_id);
CREATE INDEX ix_chit_redemptions_date    ON chit_redemptions (redeemed_on DESC);

COMMENT ON TABLE chit_redemptions IS
    'What became of a matured chit: taken as cash, or spent against an invoice. One row per redemption.';
COMMENT ON COLUMN chit_redemptions.rate_per_gram IS
    'Gold chits only: the rate the grams were valued at on the day they were spent.';
COMMENT ON COLUMN chit_redemptions.status IS
    'CANCELLED once the invoice it was applied to is cancelled; the value goes back on the chit.';

-- --- the chit's share of an invoice --------------------------------------------
ALTER TABLE sales ADD COLUMN chit_adjustment_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN sales.chit_adjustment_amount IS
    'Matured chit value applied to this invoice. Comes off the grand total, like old gold.';

-- The three totals constraints now have a second deduction to account for.
ALTER TABLE sales DROP CONSTRAINT ck_sales_non_negative;
ALTER TABLE sales DROP CONSTRAINT ck_sales_adjustment_within;
ALTER TABLE sales DROP CONSTRAINT ck_sales_net_payable;

ALTER TABLE sales ADD CONSTRAINT ck_sales_non_negative CHECK (
    subtotal >= 0 AND cgst_amount >= 0 AND sgst_amount >= 0 AND discount_amount >= 0
    AND grand_total >= 0 AND old_metal_adjustment_amount >= 0 AND chit_adjustment_amount >= 0
    AND net_payable >= 0 AND amount_paid >= 0 AND balance_amount >= 0);

ALTER TABLE sales ADD CONSTRAINT ck_sales_adjustment_within CHECK (
    old_metal_adjustment_amount + chit_adjustment_amount <= grand_total);

ALTER TABLE sales ADD CONSTRAINT ck_sales_net_payable CHECK (
    net_payable = grand_total - old_metal_adjustment_amount - chit_adjustment_amount + round_off_amount);
