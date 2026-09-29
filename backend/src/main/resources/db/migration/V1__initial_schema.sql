-- ===========================================================================
-- Jewellery Shop ERP - initial schema
--
-- Conventions used throughout:
--   * surrogate keys are BIGINT (BIGSERIAL)
--   * all timestamps are TIMESTAMPTZ, stored in UTC
--   * every business table carries the audit quadruple
--     (created_at, created_by, updated_at, updated_by)
--   * monetary / weight / percentage values are NUMERIC, never float
--   * case-insensitive uniqueness is enforced with functional unique indexes
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Security: roles, permissions, users
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name_prefix CHECK (name LIKE 'ROLE\_%')
);

COMMENT ON TABLE roles IS 'Coarse grained role. A role is simply a bundle of permissions.';

CREATE TABLE permissions (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_permissions_code           UNIQUE (code),
    CONSTRAINT uk_permissions_module_action  UNIQUE (module, action),
    CONSTRAINT ck_permissions_action         CHECK (action IN ('CREATE', 'VIEW', 'EDIT', 'DELETE'))
);

COMMENT ON TABLE permissions IS 'Fine grained MODULE_ACTION permission, e.g. ITEM_TYPE_CREATE.';

CREATE TABLE users (
    id                   BIGSERIAL    PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(150),
    mobile_number        VARCHAR(20),
    password_hash        VARCHAR(100) NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    account_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(100),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(100),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[A-Za-z0-9._-]{3,50}$'),
    CONSTRAINT ck_users_email_format    CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[A-Za-z]{2,}$'),
    CONSTRAINT ck_users_mobile_format   CHECK (mobile_number IS NULL OR mobile_number ~ '^[0-9+][0-9 -]{5,19}$')
);

-- Usernames and e-mail addresses are unique case-insensitively.
CREATE UNIQUE INDEX uk_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX uk_users_email_lower    ON users (LOWER(email)) WHERE email IS NOT NULL;
CREATE INDEX        ix_users_active         ON users (active);

COMMENT ON COLUMN users.password_hash IS 'BCrypt hash. Never returned through any API.';

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles      PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions            PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

-- Direct, per-user permission grants. Effective permissions of a user are the
-- union of their role permissions and these direct grants (purely additive -
-- there is deliberately no "deny" row, see README > Security model).
CREATE TABLE user_permissions (
    user_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_user_permissions            PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user       FOREIGN KEY (user_id)       REFERENCES users (id)       ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_permissions_permission ON user_permissions (permission_id);

-- Refresh tokens are persisted (hashed) so that logout, password reset and
-- deactivation can revoke an outstanding session immediately.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_refresh_tokens_hash   UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at)
);
CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex of the opaque refresh token; the token itself is never stored.';

-- ---------------------------------------------------------------------------
-- Masters
-- ---------------------------------------------------------------------------
CREATE TABLE item_types (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(100),
    CONSTRAINT ck_item_types_code_format CHECK (code ~ '^[A-Z0-9_-]{1,20}$'),
    CONSTRAINT ck_item_types_name_blank  CHECK (BTRIM(name) <> '')
);
CREATE UNIQUE INDEX uk_item_types_name_lower ON item_types (LOWER(name));
CREATE UNIQUE INDEX uk_item_types_code       ON item_types (code);
CREATE INDEX        ix_item_types_active     ON item_types (active);

CREATE TABLE categories (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(100),
    CONSTRAINT ck_categories_code_format CHECK (code ~ '^[A-Z0-9_-]{1,20}$'),
    CONSTRAINT ck_categories_name_blank  CHECK (BTRIM(name) <> '')
);
CREATE UNIQUE INDEX uk_categories_name_lower ON categories (LOWER(name));
CREATE UNIQUE INDEX uk_categories_code       ON categories (code);
CREATE INDEX        ix_categories_active     ON categories (active);

CREATE TABLE sub_categories (
    id          BIGSERIAL    PRIMARY KEY,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(100),
    CONSTRAINT fk_sub_categories_category    FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sub_categories_code_format CHECK (code ~ '^[A-Z0-9_-]{1,20}$'),
    CONSTRAINT ck_sub_categories_name_blank  CHECK (BTRIM(name) <> '')
);
-- The name only has to be unique inside its parent category (Men's Ring may
-- exist under both Ring and Bracelet); the code is unique shop-wide.
CREATE UNIQUE INDEX uk_sub_categories_category_name ON sub_categories (category_id, LOWER(name));
CREATE UNIQUE INDEX uk_sub_categories_code          ON sub_categories (code);
CREATE INDEX        ix_sub_categories_category      ON sub_categories (category_id);
CREATE INDEX        ix_sub_categories_active        ON sub_categories (active);

CREATE TABLE hsn_codes (
    id             BIGSERIAL     PRIMARY KEY,
    hsn_code       VARCHAR(8)    NOT NULL,
    description    VARCHAR(500),
    gst_percentage NUMERIC(5, 2) NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by     VARCHAR(100),
    CONSTRAINT ck_hsn_codes_format CHECK (hsn_code ~ '^[0-9]{4,8}$'),
    CONSTRAINT ck_hsn_codes_gst    CHECK (gst_percentage >= 0 AND gst_percentage <= 100)
);
CREATE UNIQUE INDEX uk_hsn_codes_code   ON hsn_codes (hsn_code);
CREATE INDEX        ix_hsn_codes_active ON hsn_codes (active);

CREATE TABLE purities (
    id           BIGSERIAL     PRIMARY KEY,
    item_type_id BIGINT        NOT NULL,
    name         VARCHAR(50)   NOT NULL,
    purity_value NUMERIC(6, 3) NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100),
    CONSTRAINT fk_purities_item_type  FOREIGN KEY (item_type_id) REFERENCES item_types (id) ON DELETE RESTRICT,
    CONSTRAINT ck_purities_value      CHECK (purity_value > 0 AND purity_value <= 999.999),
    CONSTRAINT ck_purities_name_blank CHECK (BTRIM(name) <> '')
);
-- 999 is a valid purity for both Gold and Silver, so uniqueness is scoped to
-- the owning item type.
CREATE UNIQUE INDEX uk_purities_item_type_name  ON purities (item_type_id, LOWER(name));
CREATE UNIQUE INDEX uk_purities_item_type_value ON purities (item_type_id, purity_value);
CREATE INDEX        ix_purities_item_type       ON purities (item_type_id);
CREATE INDEX        ix_purities_active          ON purities (active);

COMMENT ON COLUMN purities.purity_value IS 'Fineness expressed in parts per thousand, e.g. 916.000 for 22K gold.';

-- ---------------------------------------------------------------------------
-- Inventory
--
-- A row is ONE physical piece of jewellery, not a stock quantity. This is what
-- lets a future sale reference the exact piece that left the shop.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_items (
    id              BIGSERIAL      PRIMARY KEY,
    serial_number   VARCHAR(6)     NOT NULL,
    item_type_id    BIGINT         NOT NULL,
    purity_id       BIGINT         NOT NULL,
    category_id     BIGINT         NOT NULL,
    sub_category_id BIGINT,
    hsn_id          BIGINT,
    size            VARCHAR(50),
    weight_grams    NUMERIC(12, 3) NOT NULL,
    description     VARCHAR(1000),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_inventory_items_item_type    FOREIGN KEY (item_type_id)    REFERENCES item_types (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_purity       FOREIGN KEY (purity_id)       REFERENCES purities (id)       ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_category     FOREIGN KEY (category_id)     REFERENCES categories (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_sub_category FOREIGN KEY (sub_category_id) REFERENCES sub_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_hsn          FOREIGN KEY (hsn_id)          REFERENCES hsn_codes (id)      ON DELETE RESTRICT,
    -- Serial number is stored as text so that leading zeros survive (000001).
    CONSTRAINT ck_inventory_items_serial CHECK (serial_number ~ '^[0-9]{6}$'),
    CONSTRAINT ck_inventory_items_weight CHECK (weight_grams > 0)
);
CREATE UNIQUE INDEX uk_inventory_items_serial       ON inventory_items (serial_number);
CREATE INDEX        ix_inventory_items_item_type    ON inventory_items (item_type_id);
CREATE INDEX        ix_inventory_items_category     ON inventory_items (category_id);
CREATE INDEX        ix_inventory_items_sub_category ON inventory_items (sub_category_id);
CREATE INDEX        ix_inventory_items_purity       ON inventory_items (purity_id);
CREATE INDEX        ix_inventory_items_hsn          ON inventory_items (hsn_id);
CREATE INDEX        ix_inventory_items_active       ON inventory_items (active);
CREATE INDEX        ix_inventory_items_created_at   ON inventory_items (created_at DESC);

COMMENT ON TABLE  inventory_items IS 'One row = one physical jewellery piece, uniquely identified by its 6 digit serial number.';
COMMENT ON COLUMN inventory_items.weight_grams IS 'Gross weight in grams, 3 decimal places (milligram precision).';

-- ---------------------------------------------------------------------------
-- Shop settings (single row - the shop this installation belongs to)
-- ---------------------------------------------------------------------------
CREATE TABLE shop_settings (
    id                      BIGINT       PRIMARY KEY,
    shop_name               VARCHAR(150) NOT NULL,
    address_line1           VARCHAR(200),
    address_line2           VARCHAR(200),
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    pincode                 VARCHAR(10),
    mobile_number           VARCHAR(20),
    alternate_mobile_number VARCHAR(20),
    email                   VARCHAR(150),
    gstin                   VARCHAR(15),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(100),
    -- Enforces the singleton: only the row with id = 1 may ever exist.
    CONSTRAINT ck_shop_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_shop_settings_pincode   CHECK (pincode IS NULL OR pincode ~ '^[0-9]{6}$'),
    CONSTRAINT ck_shop_settings_gstin     CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$'),
    CONSTRAINT ck_shop_settings_email     CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[A-Za-z]{2,}$')
);

COMMENT ON TABLE shop_settings IS 'Shop / company profile used on invoices, receipts and GST reports.';
