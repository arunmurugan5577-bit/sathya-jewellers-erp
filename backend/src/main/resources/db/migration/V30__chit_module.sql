-- ===========================================================================
-- V30: chits - a customer saves monthly towards gold, silver or money.
--
-- The shop runs six schemes: 500, 1000, 2000, 3000, 5000 and 10,000 a month.
-- The customer pays eleven instalments and the shop puts in the twelfth, so a
-- chit is always twelve months of value for eleven months of money.
--
-- The six split into two kinds, and the difference is the whole design:
--
--   MONEY (500 / 1000 / 2000)  the balance is rupees. 500 x 12 = 6,000.
--   GOLD  (3000 / 5000 / 10000) each payment buys gold at that day's rate and
--                               the balance is grams. 3,000 at 13,675 buys
--                               0.219 g; what it is worth at the end is what
--                               gold is worth at the end.
--
-- The bonus is not a separate column. It is recorded as the twelfth payment
-- row with paid_by_shop set, so a balance is the sum of the payments on the
-- chit and nothing has to remember to add something on. For a gold chit the
-- shop enters the rate on the maturity day and the bonus buys grams at it,
-- like every other instalment.
--
-- Nothing here reckons GST or moves stock. A matured chit is spent by being
-- applied to an ordinary tax invoice, the way old gold already is, so there
-- is one place that bills and one invoice series - see V31.
-- ===========================================================================

-- --- the schemes the shop offers -------------------------------------------
CREATE TABLE chit_schemes (
    id                  BIGSERIAL      PRIMARY KEY,
    code                VARCHAR(20)    NOT NULL,
    name                VARCHAR(80)    NOT NULL,
    scheme_type         VARCHAR(10)    NOT NULL,
    monthly_amount      NUMERIC(14, 2) NOT NULL,
    payable_instalments SMALLINT       NOT NULL,
    total_instalments   SMALLINT       NOT NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          VARCHAR(100),
    CONSTRAINT ck_chit_schemes_type    CHECK (scheme_type IN ('MONEY', 'GOLD')),
    CONSTRAINT ck_chit_schemes_amount  CHECK (monthly_amount > 0),
    -- The shop always puts in exactly the months the customer does not.
    CONSTRAINT ck_chit_schemes_months  CHECK (payable_instalments > 0
                                              AND total_instalments >= payable_instalments)
);
CREATE UNIQUE INDEX uk_chit_schemes_code ON chit_schemes (code);

COMMENT ON TABLE chit_schemes IS 'The monthly amounts the shop offers. A row is a scheme, not a customer''s chit.';
COMMENT ON COLUMN chit_schemes.scheme_type IS
    'MONEY: the balance is rupees. GOLD: each payment buys grams at that day''s rate.';

INSERT INTO chit_schemes (code, name, scheme_type, monthly_amount, payable_instalments, total_instalments, created_by, updated_by) VALUES
    ('M500',   'Money 500 a month',    'MONEY',    500.00, 11, 12, 'system', 'system'),
    ('M1000',  'Money 1,000 a month',  'MONEY',   1000.00, 11, 12, 'system', 'system'),
    ('M2000',  'Money 2,000 a month',  'MONEY',   2000.00, 11, 12, 'system', 'system'),
    ('G3000',  'Gold 3,000 a month',   'GOLD',    3000.00, 11, 12, 'system', 'system'),
    ('G5000',  'Gold 5,000 a month',   'GOLD',    5000.00, 11, 12, 'system', 'system'),
    ('G10000', 'Gold 10,000 a month',  'GOLD',   10000.00, 11, 12, 'system', 'system');

-- --- a customer's chit -------------------------------------------------------
CREATE TABLE chit_accounts (
    id                  BIGSERIAL      PRIMARY KEY,
    chit_number         VARCHAR(16)    NOT NULL,
    customer_id         BIGINT         NOT NULL,
    scheme_id           BIGINT         NOT NULL,
    -- Copied at joining. A scheme's amount may be changed for new joiners and
    -- an open chit must keep the terms it was sold on.
    scheme_type         VARCHAR(10)    NOT NULL,
    monthly_amount      NUMERIC(14, 2) NOT NULL,
    payable_instalments SMALLINT       NOT NULL,
    total_instalments   SMALLINT       NOT NULL,
    started_on          DATE           NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    -- Running totals, maintained with the payments so a list screen needs no
    -- sub-query. Every one of them is the sum of chit_payments.
    paid_instalments    SMALLINT       NOT NULL DEFAULT 0,
    paid_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    accrued_grams       NUMERIC(12, 3) NOT NULL DEFAULT 0,
    -- What is left to spend. Falls as the chit is redeemed.
    balance_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    balance_grams       NUMERIC(12, 3) NOT NULL DEFAULT 0,
    matured_on          DATE,
    closed_on           DATE,
    close_reason        VARCHAR(500),
    remarks             VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          VARCHAR(100),
    CONSTRAINT fk_chit_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chit_accounts_scheme   FOREIGN KEY (scheme_id)   REFERENCES chit_schemes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_chit_accounts_number   CHECK (chit_number ~ '^[A-Z]{1,4}-[0-9]{4}-[0-9]{4,8}$'),
    CONSTRAINT ck_chit_accounts_type     CHECK (scheme_type IN ('MONEY', 'GOLD')),
    CONSTRAINT ck_chit_accounts_status   CHECK (status IN ('ACTIVE', 'COMPLETE', 'MATURED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_chit_accounts_counts   CHECK (paid_instalments >= 0 AND paid_instalments <= total_instalments),
    CONSTRAINT ck_chit_accounts_totals   CHECK (paid_amount >= 0 AND accrued_grams >= 0),
    -- Nothing can be spent that was never accrued, and nothing goes negative.
    CONSTRAINT ck_chit_accounts_balance  CHECK (balance_amount >= 0 AND balance_amount <= paid_amount
                                                AND balance_grams >= 0 AND balance_grams <= accrued_grams),
    -- A money chit holds no grams and a gold chit holds no rupees.
    CONSTRAINT ck_chit_accounts_one_kind CHECK (
        (scheme_type = 'MONEY' AND accrued_grams = 0)
        OR (scheme_type = 'GOLD' AND paid_amount >= 0)),
    CONSTRAINT ck_chit_accounts_matured  CHECK ((status IN ('MATURED', 'CLOSED')) = (matured_on IS NOT NULL)
                                                OR status = 'CANCELLED'),
    CONSTRAINT ck_chit_accounts_closed   CHECK ((status IN ('CLOSED', 'CANCELLED')) = (closed_on IS NOT NULL))
);
CREATE UNIQUE INDEX uk_chit_accounts_number   ON chit_accounts (chit_number);
CREATE INDEX        ix_chit_accounts_customer ON chit_accounts (customer_id);
CREATE INDEX        ix_chit_accounts_status   ON chit_accounts (status);
CREATE INDEX        ix_chit_accounts_started  ON chit_accounts (started_on DESC);

COMMENT ON TABLE chit_accounts IS 'One customer''s chit: the scheme they joined, what they have paid, and what is left to spend.';
COMMENT ON COLUMN chit_accounts.status IS
    'ACTIVE while paying, COMPLETE once every instalment is in, MATURED once the bonus is added, CLOSED once spent.';
COMMENT ON COLUMN chit_accounts.balance_grams IS
    'Grams still unspent. Gold chits only; a money chit carries its balance in rupees.';

-- --- the instalments ----------------------------------------------------------
CREATE TABLE chit_payments (
    id              BIGSERIAL      PRIMARY KEY,
    chit_account_id BIGINT         NOT NULL,
    instalment_no   SMALLINT       NOT NULL,
    paid_on         DATE           NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL,
    -- Gold chits only: the rate this instalment bought at, and what it bought.
    rate_per_gram   NUMERIC(14, 2),
    grams           NUMERIC(12, 3),
    -- The twelfth instalment, which the shop puts in.
    paid_by_shop    BOOLEAN        NOT NULL DEFAULT FALSE,
    payment_method  VARCHAR(20),
    reference_no    VARCHAR(100),
    remarks         VARCHAR(300),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    CONSTRAINT fk_chit_payments_account FOREIGN KEY (chit_account_id) REFERENCES chit_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_chit_payments_no      CHECK (instalment_no > 0),
    CONSTRAINT ck_chit_payments_amount  CHECK (amount > 0),
    -- A gold instalment records both or neither; a money one records neither.
    CONSTRAINT ck_chit_payments_gold    CHECK ((rate_per_gram IS NULL) = (grams IS NULL)),
    CONSTRAINT ck_chit_payments_rate    CHECK (rate_per_gram IS NULL OR rate_per_gram > 0),
    CONSTRAINT ck_chit_payments_grams   CHECK (grams IS NULL OR grams > 0),
    CONSTRAINT ck_chit_payments_method  CHECK (payment_method IS NULL
                                               OR payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER'))
);
-- One row per instalment: the same month cannot be collected twice.
CREATE UNIQUE INDEX uk_chit_payments_instalment ON chit_payments (chit_account_id, instalment_no);
CREATE INDEX        ix_chit_payments_paid_on    ON chit_payments (paid_on DESC);

COMMENT ON TABLE chit_payments IS 'One row per instalment, the shop''s bonus month included - the balance is their sum.';
COMMENT ON COLUMN chit_payments.paid_by_shop IS
    'The bonus instalment. Recorded as a payment so a balance never has to remember to add it on.';

-- --- its own number series -----------------------------------------------------
INSERT INTO document_series (code, description, prefix, separator_char, period_format, pad_width, max_length, updated_by)
VALUES ('CHIT_ACCOUNT', 'Chit account', 'CH', '-', 'FINANCIAL_YEAR', 6, 16, 'system');

-- --- permissions ----------------------------------------------------------------
INSERT INTO permissions (code, module, action, description) VALUES
    ('CHIT_VIEW',   'CHIT', 'VIEW',   'Open chits and see their balances'),
    ('CHIT_CREATE', 'CHIT', 'CREATE', 'Start a chit and record its instalments'),
    ('CHIT_EDIT',   'CHIT', 'EDIT',   'Mature a chit and redeem it'),
    ('CHIT_DELETE', 'CHIT', 'DELETE', 'Cancel or delete a chit');

-- The administrator holds every permission; V2 says so and this keeps it true.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.name = 'ROLE_ADMIN'
   AND p.code IN ('CHIT_VIEW', 'CHIT_CREATE', 'CHIT_EDIT', 'CHIT_DELETE');
