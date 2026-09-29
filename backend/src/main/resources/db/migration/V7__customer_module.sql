-- ===========================================================================
-- V7: customers.
--
-- One customer record shared by sales, old gold/silver purchases and, later,
-- returns and the customer ledger. Documents copy the customer's name and
-- address at the moment they are issued, so editing a customer here never
-- changes an invoice that has already been printed.
-- ===========================================================================

CREATE TABLE customers (
    id            BIGSERIAL    PRIMARY KEY,
    customer_code VARCHAR(20)  NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(20),
    email         VARCHAR(150),
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city          VARCHAR(100),
    state         VARCHAR(100),
    pincode       VARCHAR(6),
    gstin         VARCHAR(15),
    pan           VARCHAR(10),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    VARCHAR(100),
    CONSTRAINT uk_customers_code        UNIQUE (customer_code),
    CONSTRAINT ck_customers_name_blank  CHECK (BTRIM(full_name) <> ''),
    CONSTRAINT ck_customers_mobile      CHECK (mobile_number IS NULL OR mobile_number ~ '^[0-9+][0-9 -]{5,19}$'),
    CONSTRAINT ck_customers_email       CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[A-Za-z]{2,}$'),
    CONSTRAINT ck_customers_pincode     CHECK (pincode IS NULL OR pincode ~ '^[0-9]{6}$'),
    CONSTRAINT ck_customers_gstin       CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$'),
    CONSTRAINT ck_customers_pan         CHECK (pan IS NULL OR pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$')
);

-- Mobile number is deliberately NOT unique: families routinely share one phone,
-- and refusing the second family member would push staff into fake numbers.
CREATE INDEX ix_customers_name_lower ON customers (LOWER(full_name) text_pattern_ops);
CREATE INDEX ix_customers_mobile     ON customers (mobile_number);
CREATE INDEX ix_customers_active     ON customers (active);

COMMENT ON COLUMN customers.pan IS 'Optional. Collected for high-value transactions where the shop''s compliance policy requires it.';

-- --- Permissions -----------------------------------------------------------
INSERT INTO permissions (code, module, action, description) VALUES
    ('CUSTOMER_VIEW',   'CUSTOMER', 'VIEW',   'View customers'),
    ('CUSTOMER_CREATE', 'CUSTOMER', 'CREATE', 'Create customers'),
    ('CUSTOMER_EDIT',   'CUSTOMER', 'EDIT',   'Edit and activate / deactivate customers'),
    ('CUSTOMER_DELETE', 'CUSTOMER', 'DELETE', 'Delete customers who have no transactions');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.module = 'CUSTOMER'
ON CONFLICT DO NOTHING;
