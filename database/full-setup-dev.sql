-- ###########################################################################
--
--  JEWELLERY SHOP ERP - COMPLETE DATABASE SETUP (DEVELOPMENT / DEMO ONLY)
--
--  Creates the role, the database, every table, the reference data and two
--  sign-in accounts, so the application can be started and exercised end to
--  end without any manual steps.
--
--  ---------------------------------------------------------------------
--   !!  THIS FILE CONTAINS WORKING CREDENTIALS. DO NOT USE IN PRODUCTION  !!
--  ---------------------------------------------------------------------
--
--  The passwords below are stored as BCrypt hashes (cost 12), not plain text,
--  but the matching plain-text passwords are written in this file and in your
--  shell history. Anyone who reads the file can sign in.
--
--  For a real shop, do NOT run Part 6. Let Flyway create the schema and let
--  the application create the first administrator from the INITIAL_ADMIN_*
--  environment variables - see README.md > Default administrator bootstrap.
--
--  ===========================================================================
--  HOW TO RUN
--  ===========================================================================
--
--    psql -U postgres -f full-setup-dev.sql
--
--  This file uses psql meta-commands (\connect, \gexec), so it must be run
--  through psql - not pasted into a generic SQL tool. In pgAdmin, use
--  Tools > Query Tool only for Parts 2-7 after creating the database by hand.
--
--  It is safe to re-run ONLY after dropping the database (see Part 0).
--
--  ===========================================================================
--  WHAT YOU GET
--  ===========================================================================
--
--    Database          jewellery_erp
--    DB role           jewellery  /  jewellery
--
--    Application sign-in accounts:
--
--      Username   Password     Role         What they can see
--      ---------  -----------  -----------  -------------------------------
--      admin      Admin@123    ROLE_ADMIN   Everything (every permission)
--      staff      Staff@123    ROLE_USER    Counter role: inventory, new
--                                           sales, customers, old gold
--                                           purchase, read-only masters.
--                                           Cannot cancel sales or export
--                                           reports - use this account to
--                                           watch buttons disappear.
--
--  ===========================================================================
--  AFTER RUNNING THIS FILE
--  ===========================================================================
--
--  The tables already exist, so Flyway must be told not to re-create them.
--  Start the backend once with a baseline at version 24:
--
--    mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
--      -Dspring.flyway.baseline-on-migrate=true \
--      -Dspring.flyway.baseline-version=24"
--
--  (start-backend.ps1 already passes these.) Flyway then records the schema as
--  being at V24 and applies only later migrations.
--  Omit those flags on every later start.
--
--  Alternatively, skip this file entirely and let Flyway build the schema from
--  scratch - then run Part 6 on its own to add the two accounts.
--
-- ###########################################################################


-- ===========================================================================
-- PART 0 - START CLEAN  (optional; uncomment to wipe and rebuild)
-- ===========================================================================
-- Disconnect anything still holding the database open, then drop it.
--
-- SELECT pg_terminate_backend(pid)
--   FROM pg_stat_activity
--  WHERE datname = 'jewellery_erp' AND pid <> pg_backend_pid();
--
-- DROP DATABASE IF EXISTS jewellery_erp;
-- DROP ROLE IF EXISTS jewellery;


-- ===========================================================================
-- PART 1 - ROLE AND DATABASE
--
-- Run while connected to any database (psql connects to "postgres" by
-- default). Both steps are idempotent, so a partial earlier run is harmless.
-- ===========================================================================

-- Abort on the first error instead of cascading failures through later parts.
\set ON_ERROR_STOP on

\echo '== Part 1: creating role and database =='

-- The login role the application connects as.
DO $setup$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jewellery') THEN
        CREATE ROLE jewellery WITH LOGIN PASSWORD 'jewellery';
        RAISE NOTICE 'Created role "jewellery".';
    ELSE
        RAISE NOTICE 'Role "jewellery" already exists - left unchanged.';
    END IF;
END
$setup$;

-- CREATE DATABASE cannot run inside a transaction or a DO block, so it is
-- generated as text and executed by psql's \gexec.
SELECT 'CREATE DATABASE jewellery_erp OWNER jewellery ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'jewellery_erp')
\gexec

-- Switch into the new database. Everything below runs there.
\connect jewellery_erp

-- On PostgreSQL 15+ the public schema no longer grants CREATE to everyone, so
-- ownership is set explicitly rather than relied upon.
ALTER SCHEMA public OWNER TO jewellery;
GRANT ALL ON SCHEMA public TO jewellery;

-- Create every object as the application role, so the application can write to
-- what it reads. Without this the tables would be owned by "postgres".
SET ROLE jewellery;

\echo '== Part 1 complete =='


-- ===========================================================================
-- PART 2 - schema
--
-- Copied verbatim from V1__initial_schema.sql so that this script and the
-- Flyway migration can never disagree about the schema.
-- ===========================================================================

\echo '== Part 2: schema =='

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


-- ===========================================================================
-- PART 3 - roles and permissions
--
-- Copied verbatim from V2__seed_roles_and_permissions.sql so that this script and the
-- Flyway migration can never disagree about the schema.
-- ===========================================================================

\echo '== Part 3: roles and permissions =='

-- ===========================================================================
-- Seed the permission catalogue and the two initial roles.
--
-- NOTE: no user rows and no passwords are created here. The first
-- administrator is created at application start-up from environment
-- variables (see InitialAdminBootstrap) so that no credential ever lives in
-- source control.
-- ===========================================================================

-- --- Permissions -----------------------------------------------------------
-- Every module that supports the full CRUD action set.
INSERT INTO permissions (code, module, action, description)
SELECT m.module || '_' || a.action,
       m.module,
       a.action,
       a.label || ' ' || m.label
FROM (VALUES
          ('ITEM_TYPE',    'item types'),
          ('CATEGORY',     'categories'),
          ('SUB_CATEGORY', 'sub categories'),
          ('HSN',          'HSN codes'),
          ('PURITY',       'purities'),
          ('USER',         'users'),
          ('INVENTORY',    'inventory items')
     ) AS m (module, label)
CROSS JOIN (VALUES
          ('VIEW',   'View'),
          ('CREATE', 'Create'),
          ('EDIT',   'Edit'),
          ('DELETE', 'Delete')
     ) AS a (action, label);

-- Shop settings is a singleton profile: it can be viewed and edited, never
-- created or deleted.
INSERT INTO permissions (code, module, action, description) VALUES
    ('SHOP_SETTINGS_VIEW', 'SHOP_SETTINGS', 'VIEW', 'View shop settings'),
    ('SHOP_SETTINGS_EDIT', 'SHOP_SETTINGS', 'EDIT', 'Edit shop settings');

-- --- Roles -----------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN', 'Administrator - holds every permission in the system'),
    ('ROLE_USER',  'Standard user - holds no permission by default; access is granted per user');

-- --- Role permissions ------------------------------------------------------
-- ROLE_ADMIN receives every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN';

-- ROLE_USER deliberately receives none: a staff member sees nothing until an
-- administrator grants individual permissions on the user permission screen.
-- This keeps "add a new module" from silently widening staff access.


-- ===========================================================================
-- PART 4 - shop settings
--
-- Copied verbatim from V3__seed_shop_settings.sql so that this script and the
-- Flyway migration can never disagree about the schema.
-- ===========================================================================

\echo '== Part 4: shop settings =='

-- ===========================================================================
-- Create the singleton shop settings row so that GET /api/shop-settings always
-- has something to return. The values are placeholders the shop owner edits on
-- the Settings screen; nothing here is used for calculations.
-- ===========================================================================
INSERT INTO shop_settings (id, shop_name, created_by, updated_by)
VALUES (1, 'My Jewellery Shop', 'system', 'system');


-- ===========================================================================
-- PART 5 - master reference data
--
-- Copied verbatim from V4__seed_master_defaults.sql so that this script and the
-- Flyway migration can never disagree about the schema.
-- ===========================================================================

\echo '== Part 5: master reference data =='

-- ===========================================================================
-- Reference data every Indian jewellery shop needs on day one.
--
-- This is convenience seed data, not system data: all of it is editable and
-- deactivatable from the Masters screens. Remove this migration before the
-- first deployment if the shop prefers to key in its own masters.
-- ===========================================================================

-- --- Item types ------------------------------------------------------------
INSERT INTO item_types (name, code, description, created_by, updated_by) VALUES
    ('Gold',     'GOLD', 'Gold jewellery',     'system', 'system'),
    ('Silver',   'SILV', 'Silver jewellery',   'system', 'system'),
    ('Platinum', 'PLAT', 'Platinum jewellery', 'system', 'system'),
    ('Diamond',  'DIAM', 'Diamond jewellery',  'system', 'system');

-- --- Purities (fineness in parts per thousand) -----------------------------
INSERT INTO purities (item_type_id, name, purity_value, description, created_by, updated_by)
SELECT it.id, v.name, v.purity_value, v.description, 'system', 'system'
FROM item_types it
JOIN (VALUES
          ('GOLD', '24K / 999', 999.000, '24 carat gold, 99.9% pure'),
          ('GOLD', '22K / 916', 916.000, '22 carat gold, hallmark 916'),
          ('GOLD', '18K / 750', 750.000, '18 carat gold, hallmark 750'),
          ('GOLD', '14K / 585', 585.000, '14 carat gold, hallmark 585'),
          ('SILV', '999',       999.000, 'Fine silver'),
          ('SILV', '925',       925.000, 'Sterling silver'),
          ('PLAT', '950',       950.000, 'Platinum 950')
     ) AS v (type_code, name, purity_value, description)
  ON v.type_code = it.code;

-- --- Categories ------------------------------------------------------------
INSERT INTO categories (name, code, description, created_by, updated_by) VALUES
    ('Ring',     'RING', 'Finger rings',            'system', 'system'),
    ('Chain',    'CHAN', 'Neck chains',             'system', 'system'),
    ('Necklace', 'NECK', 'Necklaces and haram',     'system', 'system'),
    ('Bangle',   'BANG', 'Bangles and kada',        'system', 'system'),
    ('Bracelet', 'BRAC', 'Bracelets',               'system', 'system'),
    ('Earring',  'EARR', 'Earrings, jhumka, studs', 'system', 'system'),
    ('Pendant',  'PEND', 'Pendants and lockets',    'system', 'system');

-- --- Sub categories --------------------------------------------------------
INSERT INTO sub_categories (category_id, name, code, description, created_by, updated_by)
SELECT c.id, v.name, v.code, v.description, 'system', 'system'
FROM categories c
JOIN (VALUES
          ('RING', 'Mens Ring',    'RING-M',  'Rings for men'),
          ('RING', 'Womens Ring',  'RING-W',  'Rings for women'),
          ('RING', 'Kids Ring',    'RING-K',  'Rings for children'),
          ('CHAN', 'Mens Chain',   'CHAN-M',  'Chains for men'),
          ('CHAN', 'Womens Chain', 'CHAN-W',  'Chains for women'),
          ('BANG', 'Plain Bangle', 'BANG-P',  'Plain bangles'),
          ('BANG', 'Stone Bangle', 'BANG-S',  'Stone studded bangles'),
          ('EARR', 'Jhumka',       'EARR-J',  'Jhumka earrings'),
          ('EARR', 'Stud',         'EARR-S',  'Stud earrings')
     ) AS v (category_code, name, code, description)
  ON v.category_code = c.code;

-- --- HSN codes -------------------------------------------------------------
-- 3% GST applies to articles of jewellery under HSN 7113 / 7118 in India.
INSERT INTO hsn_codes (hsn_code, description, gst_percentage, created_by, updated_by) VALUES
    ('7113', 'Articles of jewellery of precious metal',   3.00, 'system', 'system'),
    ('7114', 'Articles of goldsmiths or silversmiths',    3.00, 'system', 'system'),
    ('7118', 'Coin (gold / silver coins)',                3.00, 'system', 'system'),
    ('7102', 'Diamonds, whether or not worked',           0.25, 'system', 'system'),
    ('9988', 'Job work - manufacture of jewellery',       5.00, 'system', 'system');

-- ===========================================================================
-- PART 5B - CUSTOMERS, OLD GOLD / SILVER, SALES, REPORTS  (Flyway V5 - V11)
--
-- Each block below is copied verbatim from its Flyway migration, so this
-- script and the migrations can never disagree about the schema.
-- ===========================================================================

\echo '== Part 5B: customers, old gold / silver, sales, reports =='

-- ---------------------------------------------------------------------------
-- V5__permission_export_action.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V5: allow EXPORT as a permission action.
--
-- Reports separate "may see the report" (VIEW) from "may take the data out of
-- the system as a spreadsheet" (EXPORT). The original check constraint only
-- knew the four CRUD actions, so it is widened here rather than edited in V1 -
-- an applied migration is never changed.
-- ===========================================================================

ALTER TABLE permissions DROP CONSTRAINT ck_permissions_action;

ALTER TABLE permissions
    ADD CONSTRAINT ck_permissions_action
    CHECK (action IN ('CREATE', 'VIEW', 'EDIT', 'DELETE', 'EXPORT'));

-- ---------------------------------------------------------------------------
-- V6__document_numbering.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V6: configurable, gap-free document numbering.
--
-- Why not a PostgreSQL SEQUENCE: a sequence value handed to a transaction that
-- later rolls back is lost for ever, which leaves gaps. Indian GST rules require
-- tax invoices to carry a consecutive serial number, so a failed sale must not
-- burn one. A counter row locked FOR UPDATE inside the same transaction as the
-- document rolls back with it.
--
-- Format, assembled by DocumentNumberService:
--     [prefix][sep][period][sep][zero-padded sequence]
--     INV-2026-000001   (financial year 2026-27, which starts 1 April 2026)
--
-- max_length defaults to 16 for tax documents: the GST invoice rules cap the
-- invoice serial number at sixteen characters.
-- ===========================================================================

CREATE TABLE document_series (
    code           VARCHAR(30)  PRIMARY KEY,
    description    VARCHAR(150) NOT NULL,
    prefix         VARCHAR(10)  NOT NULL DEFAULT '',
    separator_char VARCHAR(1)   NOT NULL DEFAULT '-',
    period_format  VARCHAR(20)  NOT NULL,
    pad_width      SMALLINT     NOT NULL,
    max_length     SMALLINT     NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(100),
    CONSTRAINT ck_document_series_prefix    CHECK (prefix ~ '^[A-Z0-9]*$'),
    CONSTRAINT ck_document_series_separator CHECK (separator_char IN ('', '-', '/')),
    CONSTRAINT ck_document_series_period    CHECK (period_format IN ('NONE', 'CALENDAR_YEAR', 'FINANCIAL_YEAR')),
    CONSTRAINT ck_document_series_padding   CHECK (pad_width BETWEEN 1 AND 12),
    CONSTRAINT ck_document_series_length    CHECK (max_length BETWEEN 4 AND 30)
);

COMMENT ON TABLE document_series IS 'Numbering rules per document type. Edit a row to change a format; history is unaffected.';

CREATE TABLE document_counters (
    series_code VARCHAR(30) NOT NULL,
    period_key  VARCHAR(10) NOT NULL,
    next_value  BIGINT      NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_document_counters        PRIMARY KEY (series_code, period_key),
    CONSTRAINT fk_document_counters_series FOREIGN KEY (series_code) REFERENCES document_series (code) ON DELETE RESTRICT,
    CONSTRAINT ck_document_counters_next   CHECK (next_value > 0)
);

COMMENT ON TABLE document_counters IS 'Next number per series and period. period_key is ALL for series that never reset.';

INSERT INTO document_series (code, description, prefix, separator_char, period_format, pad_width, max_length, updated_by) VALUES
    ('SALE_INVOICE',       'Sales tax invoice',               'INV', '-', 'FINANCIAL_YEAR', 6, 16, 'system'),
    ('OLD_METAL_PURCHASE', 'Old gold / silver purchase bill', 'PB',  '-', 'FINANCIAL_YEAR', 6, 16, 'system'),
    ('CUSTOMER',           'Customer code',                   'CUS', '-', 'NONE',           6, 20, 'system');

-- ---------------------------------------------------------------------------
-- V7__customer_module.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V8__inventory_sale_status.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V8: inventory availability.
--
-- Until now an inventory row only had `active`, an administrative on/off flag.
-- Selling needs a separate fact - has this physical piece left the shop? - so
-- that the same piece can never be invoiced twice. The two are independent:
--
--     sellable  =  status = 'AVAILABLE'  AND  active
--
-- Existing rows default to AVAILABLE, which is true of all current stock.
-- This is deliberately not the future inventory-movement ledger; it is the
-- single fact that selling requires.
-- ===========================================================================

ALTER TABLE inventory_items
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_status CHECK (status IN ('AVAILABLE', 'SOLD'));

CREATE INDEX ix_inventory_items_status ON inventory_items (status);

COMMENT ON COLUMN inventory_items.status IS 'AVAILABLE until invoiced; SOLD afterwards. Returns will add further states.';

-- The shop's tax invoice lists this figure as the NET weight - wastage is added
-- on top at billing to reach the gross (chargeable) weight. The original comment
-- called it gross; corrected here.
COMMENT ON COLUMN inventory_items.weight_grams IS 'Net weight of the piece in grams, 3 decimal places. Gross weight is computed at sale time as net + wastage.';

-- ---------------------------------------------------------------------------
-- V9__old_metal_module.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V10__sales_module.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V11__report_permissions.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V11: report permissions.
--
-- VIEW   = may open the report screen and see the on-screen preview
-- EXPORT = may download the full report as an .xlsx file
--
-- Kept apart because exporting takes the whole dataset out of the application,
-- which is a bigger decision than looking at it.
-- ===========================================================================

INSERT INTO permissions (code, module, action, description) VALUES
    ('REPORT_STOCK_VIEW',   'REPORT_STOCK', 'VIEW',   'View the stock report'),
    ('REPORT_STOCK_EXPORT', 'REPORT_STOCK', 'EXPORT', 'Download the stock report as Excel'),
    ('REPORT_SALES_VIEW',   'REPORT_SALES', 'VIEW',   'View the sales report'),
    ('REPORT_SALES_EXPORT', 'REPORT_SALES', 'EXPORT', 'Download the sales report as Excel');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.module IN ('REPORT_STOCK', 'REPORT_SALES')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- V12__label_printing.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V13__label_multi_up.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V13: labels that come several across the roll.
--
-- The shop's tags are jewellery "dumbbell" tags: a printable head with a long
-- narrow tail that wraps round the piece, five tags side by side across the
-- roll. The printer advances one ROW of five at a time, so a page has to be the
-- whole row - a page the size of one tag makes the printer feed (and waste) a
-- whole row per label.
--
--   labels_across     tags side by side across the roll; page width is
--                     label_width_mm x labels_across
--   content_height_mm how much of the tag's length is printable - the head.
--                     The rest is the tail, left blank.
--
-- The values set below are measured from a photograph of the shop's roll and
-- are a starting point: print one row, measure it, and adjust on the Label
-- settings screen.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN labels_across     SMALLINT     NOT NULL DEFAULT 1,
    ADD COLUMN content_height_mm NUMERIC(6,2) NOT NULL DEFAULT 25.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_across  CHECK (labels_across BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_label_settings_content CHECK (
        content_height_mm > 0 AND content_height_mm <= label_height_mm);

COMMENT ON COLUMN label_settings.labels_across IS
    'Tags side by side across the roll. One printed page is one row of this many.';
COMMENT ON COLUMN label_settings.content_height_mm IS
    'Printable height at the top of each tag (the head). The tail below it stays blank.';

-- Measured from the shop's own roll: five tags across, each about 21 mm wide and
-- 65 mm long, with roughly the top 22 mm printable.
UPDATE label_settings
   SET label_width_mm    = 21.00,
       label_height_mm   = 65.00,
       labels_across     = 5,
       content_height_mm = 22.00,
       margin_top_mm     = 1.50,
       margin_left_mm    = 1.50,
       barcode_height_mm = 8.00,
       barcode_module_mm = 0.200,
       serial_font_pt    = 5.50,
       purity_font_pt    = 5.00,
       shop_font_pt      = 5.50,
       updated_by        = 'system'
 WHERE id = 1;

-- ---------------------------------------------------------------------------
-- V14__label_detail_font.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V14: type size for the item details printed beside the barcode.
--
-- The label now carries the piece's name, weight and size in a block to the
-- right of the barcode (KOLUSU / GMS.32.130 / Size:123456), which needs a size
-- of its own - it is usually set smaller than the serial number.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN detail_font_pt NUMERIC(5,2) NOT NULL DEFAULT 6.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_detail_font CHECK (detail_font_pt BETWEEN 3 AND 30);

COMMENT ON COLUMN label_settings.detail_font_pt IS
    'Type size for the item name, weight and size printed beside the barcode.';

-- ---------------------------------------------------------------------------
-- V15__label_tag_geometry.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V15: the shop's actual tag size.
--
-- The printable canvas of one jewellery tag is 60 x 12 mm, one tag per feed -
-- not the 21 x 65 mm, five-across guess taken from a photograph in V13. That
-- guess is what made the printer spit out four blank tags per label: a 65 mm
-- page on 12 mm media advances roughly five tags before the next gap.
--
-- 60 x 12 mm also gives room for the layout the shop asked for, side by side:
--
--      ||||||||||     WOMENS RING
--   SJ ||||||||||     GMS.20.800
--      905351 22K / 916   Size:11
--
-- Barcode: 6 digits of Code 128 at a 0.25 mm narrow bar is about 17 mm, so the
-- bars, the short name and the details block all fit across 60 mm with room to
-- spare. Height: 6.5 mm of bars plus the serial line fits inside 12 mm with a
-- 1 mm margin top and bottom.
-- ===========================================================================

UPDATE label_settings
   SET label_width_mm    = 60.00,
       label_height_mm   = 12.00,
       labels_across     = 1,
       content_height_mm = 12.00,
       margin_top_mm     = 1.00,
       margin_left_mm    = 1.50,
       offset_x_mm       = 0.00,
       offset_y_mm       = 0.00,
       barcode_height_mm = 6.50,
       barcode_module_mm = 0.250,
       serial_font_pt    = 6.50,
       purity_font_pt    = 6.00,
       shop_font_pt      = 7.00,
       detail_font_pt    = 6.00,
       updated_by        = 'system'
 WHERE id = 1;

-- ---------------------------------------------------------------------------
-- V16__label_printer.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V16: which printer the labels go to.
--
-- Labels are now sent straight from the application to the Windows printer, so
-- there is no browser print dialog to pick the label printer in - and no chance
-- of a tag being laid out on A4 because the dialog was left on the office
-- printer. The name is the Windows printer name, e.g. "SNBC TVSE LP 46 NEO BPLE".
--
-- Empty means "use the machine's default printer".
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN printer_name VARCHAR(160);

COMMENT ON COLUMN label_settings.printer_name IS
    'Windows printer the labels are sent to. Empty uses the default printer.';

-- ---------------------------------------------------------------------------
-- V17__label_content_area.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V17: the part of the tag that can actually be printed on.
--
-- A jewellery dumbbell tag is not printable end to end: it has a head, a thin
-- neck and a tail that wraps round the piece. Print placed over the neck is
-- clipped top and bottom. content_width_mm says how far along the tag print
-- may go; content_height_mm (V13) says how far down.
--
-- The default is the whole label. A shop whose print lands on the neck sets
-- this to the width of the head on the Label Settings screen.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN content_width_mm NUMERIC(6,2);

UPDATE label_settings SET content_width_mm = label_width_mm WHERE content_width_mm IS NULL;

ALTER TABLE label_settings
    ALTER COLUMN content_width_mm SET NOT NULL;

ALTER TABLE label_settings
    ALTER COLUMN content_width_mm SET DEFAULT 60.00;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_content_width CHECK (
        content_width_mm > 0 AND content_width_mm <= label_width_mm);

COMMENT ON COLUMN label_settings.content_width_mm IS
    'Printable length of the tag, from its left edge. Anything beyond it (neck, tail) is left blank.';

-- ---------------------------------------------------------------------------
-- V18__label_negative_left_margin.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V18: let the left margin go negative.
--
-- A printer driver may lay the page down some way into the tag, so the print
-- starts past the leading edge however the layout is set up. A negative left
-- margin drags the artwork back the other way; the printer grows the page to
-- the left to match, so nothing is clipped.
--
-- The top margin stays at zero or more: a negative one would only push print
-- off the edge of the tag.
-- ===========================================================================

ALTER TABLE label_settings
    DROP CONSTRAINT ck_label_settings_margins;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_margins CHECK (
        margin_top_mm >= 0 AND margin_top_mm < label_height_mm
        AND margin_left_mm > -label_width_mm AND margin_left_mm < label_width_mm);

COMMENT ON COLUMN label_settings.margin_left_mm IS
    'Where the artwork starts along the tag. Negative pulls it back towards the leading edge.';

-- ---------------------------------------------------------------------------
-- V19__label_shop_mark.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V19: the shop's mark on the tag - its short name, or its logo.
--
-- The logo reads well even at label size: at 203 dpi a 6.5 mm box is 52 dots,
-- enough for the monogram to come out as white knocked from a dark disc. The
-- artwork lives in the application at /branding/label-logo.png.
--
-- The short name stays required either way: it is the fallback when the logo
-- cannot be loaded, and switching back to TEXT must not need a second edit.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN shop_mark VARCHAR(10) NOT NULL DEFAULT 'LOGO';

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_shop_mark CHECK (shop_mark IN ('TEXT', 'LOGO', 'NONE'));

ALTER TABLE label_settings
    ADD COLUMN shop_logo_height_mm NUMERIC(5,2) NOT NULL DEFAULT 6.50;

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_logo_height CHECK (
        shop_logo_height_mm >= 2 AND shop_logo_height_mm <= 40);

COMMENT ON COLUMN label_settings.shop_mark IS
    'What is printed beside the barcode: the shop LOGO, its short name as TEXT, or NONE.';
COMMENT ON COLUMN label_settings.shop_logo_height_mm IS
    'Height of the square box the logo is drawn in. Below about 4 mm the monogram stops reading.';

-- ---------------------------------------------------------------------------
-- V20__label_print_agent.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V20: printing from a server that is not in the shop.
--
-- Direct printing works by asking the operating system the application runs on
-- for its printers. That is fine while the application runs on the counter PC.
-- Hosted, it asks a machine in a data centre, which has no printers and no way
-- to reach the ones on the shop's USB cable.
--
-- So the server stops printing and starts queueing. It renders each page to a
-- bitmap at the printer's own resolution - the same drawing code, so the tag
-- comes out identical - and a small agent on the shop PC collects the pages and
-- spools them to the real printer.
--
--   print_mode = DIRECT   the printer is on this machine (unchanged behaviour)
--   print_mode = AGENT    queue the pages for the shop's print agent
--
-- DIRECT stays the default: an existing install must not change how it prints
-- because of a migration.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN print_mode VARCHAR(10) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_print_mode CHECK (print_mode IN ('DIRECT', 'AGENT'));

COMMENT ON COLUMN label_settings.print_mode IS
    'DIRECT prints from this machine; AGENT queues pages for the print agent in the shop.';

-- --- the queue ------------------------------------------------------------
-- One row per printed page. Pages rather than jobs so that a run which fails
-- half way can be retried from where it stopped, and so the agent never has to
-- hold a whole batch in memory.
CREATE TABLE label_print_queue (
    id              BIGSERIAL     PRIMARY KEY,
    job_id          BIGINT        NOT NULL REFERENCES label_print_jobs (id),
    page_no         INTEGER       NOT NULL,
    image           BYTEA         NOT NULL,
    width_mm        NUMERIC(6,2)  NOT NULL,
    height_mm       NUMERIC(6,2)  NOT NULL,
    printer_name    VARCHAR(160),
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    claimed_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    error_message   VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_label_queue_status CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'FAILED')),
    CONSTRAINT ck_label_queue_page   CHECK (page_no >= 1),
    CONSTRAINT uq_label_queue_page   UNIQUE (job_id, page_no)
);

-- The agent asks for the oldest pending pages, over and over. This is the only
-- query that matters for its latency.
CREATE INDEX ix_label_queue_pending ON label_print_queue (status, id)
    WHERE status IN ('PENDING', 'CLAIMED');

COMMENT ON TABLE label_print_queue IS
    'Pages waiting for the shop print agent. One row per printed page, in order.';
COMMENT ON COLUMN label_print_queue.image IS
    'The page as a 1-bit PNG at the printer resolution, rendered by the server.';

-- --- what the agent tells us about itself ---------------------------------
CREATE TABLE label_print_agent (
    id              SMALLINT      PRIMARY KEY,
    last_seen_at    TIMESTAMPTZ,
    agent_version   VARCHAR(40),
    host_name       VARCHAR(160),
    printers        TEXT,
    default_printer VARCHAR(160),
    CONSTRAINT ck_label_agent_singleton CHECK (id = 1)
);

INSERT INTO label_print_agent (id) VALUES (1);

COMMENT ON TABLE label_print_agent IS
    'Last contact from the shop print agent, and the printers it can see there.';
COMMENT ON COLUMN label_print_agent.printers IS
    'Newline separated printer names, as reported by the shop PC.';

-- --- the agent's account ------------------------------------------------
-- The agent signs in as an ordinary user and reuses LABEL_CREATE. A separate
-- authority would need a new action, and the permissions table allows only
-- CREATE, VIEW, EDIT and DELETE - not worth reshaping the model for one
-- machine account whose password lives on the shop PC and nowhere else.

-- ---------------------------------------------------------------------------
-- V21__wholesale_estimates.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V22__inventory_serial_numbering.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V23__serial_three_digits.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- V24__label_no_logo.sql
-- ---------------------------------------------------------------------------
-- ===========================================================================
-- V24: the tag carries the shop's short name, not its logo.
--
-- The logo reads at 203 dpi, but it is a solid dark square on a tag only 12 mm
-- tall, and the shop does not want it there. The short name is what a jeweller
-- actually needs beside the barcode: it says whose stock the piece is when a
-- tag turns up loose in a tray.
--
-- LOGO stays a choice on the settings screen - the artwork, the layout and the
-- thresholding are all still in place - so turning it back on is one dropdown
-- and no migration. Only the default changes, and this shop's current setting.
-- ===========================================================================

ALTER TABLE label_settings
    ALTER COLUMN shop_mark SET DEFAULT 'TEXT';

UPDATE label_settings
   SET shop_mark  = 'TEXT',
       updated_by = 'system'
 WHERE id = 1;

COMMENT ON COLUMN label_settings.shop_mark IS
    'What is printed beside the barcode: the shop short name as TEXT (default), its LOGO, or NONE.';

-- --- Demo shop details -----------------------------------------------------
-- Printed at the top of every invoice and purchase bill. Placeholder values:
-- correct them on the Shop Settings screen. GSTIN is deliberately left empty -
-- enter the shop's real GSTIN there before issuing tax invoices.
UPDATE shop_settings
   SET shop_name  = 'Sathya Jewellers',
       city       = 'Udumalpet',
       state      = 'Tamil Nadu',
       updated_by = 'setup-script'
 WHERE id = 1;

-- --- OPTIONAL: continue the paper invoice book ------------------------------
-- By default invoices are numbered INV-2026-000001, INV-2026-000002, ... and
-- restart every financial year (April). To carry on from the paper book -
-- e.g. the last paper invoice was No. 150 - uncomment and adjust:
--
-- INSERT INTO document_counters (series_code, period_key, next_value)
-- VALUES ('SALE_INVOICE', '2026', 151)
-- ON CONFLICT (series_code, period_key) DO UPDATE SET next_value = EXCLUDED.next_value;
--
-- To print plain numbers (151, 152 ...) with no prefix or year, also run:
--
-- UPDATE document_series
--    SET prefix = '', separator_char = NULL, period_format = 'NONE', pad_width = 1
--  WHERE code = 'SALE_INVOICE';
-- (then use period_key 'ALL' in the counter insert above instead of '2026')

\echo '== Part 5B complete =='


-- ===========================================================================
-- PART 6 - SIGN-IN ACCOUNTS  (DEVELOPMENT / DEMO ONLY)
--
--  !! Everything in this part is the exception to the project's own rule that
--  !! no credential lives in source control. It exists so the application can
--  !! be opened and exercised immediately. Delete these accounts, or drop the
--  !! database, before the shop goes live.
--
-- The password_hash values are real BCrypt hashes at cost 12, matching
-- BCryptPasswordEncoder(12) in SecurityConfig. They were generated for these
-- exact passwords and verified to round-trip:
--
--     admin  ->  Admin@123
--     staff  ->  Staff@123
--
-- must_change_password is FALSE so that both accounts land straight on the
-- dashboard. In a real deployment the bootstrap runner sets it to TRUE, which
-- forces the change-password screen before anything else.
-- ===========================================================================

\echo '== Part 6: creating demo sign-in accounts =='

-- --- The administrator -----------------------------------------------------
INSERT INTO users (
    username, full_name, email, mobile_number, password_hash,
    active, account_locked, must_change_password, created_by, updated_by
) VALUES (
    'admin',
    'Shop Administrator',
    'admin@example.com',
    '9000000001',
    '$2b$12$kxqZzbe6NnotusCftBukNuh7KeFO2nvSy0HEbd4EfBFDxqIREmwP2',  -- Admin@123
    TRUE, FALSE, FALSE, 'setup-script', 'setup-script'
);

-- --- A limited staff user, to demonstrate the permission model -------------
INSERT INTO users (
    username, full_name, email, mobile_number, password_hash,
    active, account_locked, must_change_password, created_by, updated_by
) VALUES (
    'staff',
    'Counter Staff',
    'staff@example.com',
    '9000000002',
    '$2b$12$aVlXEputHwsoWKqc3yC8tu7iqVPLVt6jObq1L09LWXIuseQ4MUUu2',  -- Staff@123
    TRUE, FALSE, FALSE, 'setup-script', 'setup-script'
);

-- --- Role assignment -------------------------------------------------------
-- ROLE_ADMIN already carries every permission (Part 3), so the administrator
-- needs no direct grants at all.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.username = 'admin';

-- ROLE_USER deliberately carries NO permissions. That is the design: a staff
-- account can sign in and see the dashboard, and nothing more, until an
-- administrator grants access explicitly.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_USER'
WHERE u.username = 'staff';

-- --- Direct permission grants for the staff user ---------------------------
-- A realistic counter role: can add and look up stock, bill sales, register
-- customers, buy old gold, and preview reports - but cannot cancel an invoice,
-- take further payments, export reports to Excel, edit masters, delete
-- anything, or see Users and Shop Settings at all.
--
-- Sign in as this user to watch the sidebar shrink and the Edit / Delete
-- buttons disappear. Every one of those actions is also refused by the server
-- if called directly, which is what actually enforces the rule.
INSERT INTO user_permissions (user_id, permission_id)
SELECT u.id, p.id
FROM users u
CROSS JOIN permissions p
WHERE u.username = 'staff'
  AND p.code IN (
      'INVENTORY_VIEW',
      'INVENTORY_CREATE',
      'INVENTORY_EDIT',
      'ITEM_TYPE_VIEW',
      'CATEGORY_VIEW',
      'SUB_CATEGORY_VIEW',
      'PURITY_VIEW',
      'HSN_VIEW',
      'CUSTOMER_VIEW',
      'CUSTOMER_CREATE',
      'OLD_METAL_VIEW',
      'OLD_METAL_CREATE',
      'SALES_VIEW',
      'SALES_CREATE',
      'REPORT_STOCK_VIEW',
      'REPORT_SALES_VIEW',
      'LABEL_VIEW',
      'LABEL_CREATE'
  );

\echo '== Part 6 complete =='


-- ===========================================================================
-- PART 7 - VERIFICATION
--
-- Prints what was created. If any count is zero, something above failed.
-- ===========================================================================

\echo ''
\echo '== Part 7: verification =='

SELECT 'tables'           AS item, count(*)::text AS value FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
UNION ALL
SELECT 'permissions',       count(*)::text FROM permissions
UNION ALL
SELECT 'roles',             count(*)::text FROM roles
UNION ALL
SELECT 'admin permissions', count(*)::text FROM role_permissions rp
    JOIN roles r ON r.id = rp.role_id WHERE r.name = 'ROLE_ADMIN'
UNION ALL
SELECT 'item types',        count(*)::text FROM item_types
UNION ALL
SELECT 'purities',          count(*)::text FROM purities
UNION ALL
SELECT 'categories',        count(*)::text FROM categories
UNION ALL
SELECT 'sub categories',    count(*)::text FROM sub_categories
UNION ALL
SELECT 'hsn codes',         count(*)::text FROM hsn_codes
UNION ALL
SELECT 'shop settings row', count(*)::text FROM shop_settings
UNION ALL
SELECT 'users',             count(*)::text FROM users
UNION ALL
SELECT 'document series',   count(*)::text FROM document_series
ORDER BY item;

\echo ''
\echo 'Accounts and their effective permission counts:'

-- Effective permissions = role permissions UNION direct grants. This is the
-- same rule the application applies when it builds a token.
SELECT u.username,
       u.full_name,
       string_agg(DISTINCT r.name, ', ') AS roles,
       (
           SELECT count(DISTINCT p.id)
           FROM permissions p
           WHERE p.id IN (SELECT rp.permission_id
                            FROM role_permissions rp
                            JOIN user_roles ur ON ur.role_id = rp.role_id
                           WHERE ur.user_id = u.id)
              OR p.id IN (SELECT up.permission_id
                            FROM user_permissions up
                           WHERE up.user_id = u.id)
       ) AS effective_permissions
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
GROUP BY u.id, u.username, u.full_name
ORDER BY u.username;

\echo ''
\echo '###########################################################################'
\echo '  Setup complete.'
\echo ''
\echo '  Database : jewellery_erp        DB role : jewellery / jewellery'
\echo ''
\echo '  Sign in at http://localhost:4200 with:'
\echo '    admin / Admin@123   - full access'
\echo '    staff / Staff@123   - counter role, to see permissions at work'
\echo ''
\echo '  Change both passwords from the account menu before this database is'
\echo '  used for anything real.'
\echo ''
\echo '  Start the backend ONCE with:'
\echo '    -Dspring.flyway.baseline-on-migrate=true -Dspring.flyway.baseline-version=24'
\echo '###########################################################################'
