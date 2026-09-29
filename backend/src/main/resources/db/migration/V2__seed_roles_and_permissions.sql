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
