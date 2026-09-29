-- ===========================================================================
-- Create the singleton shop settings row so that GET /api/shop-settings always
-- has something to return. The values are placeholders the shop owner edits on
-- the Settings screen; nothing here is used for calculations.
-- ===========================================================================
INSERT INTO shop_settings (id, shop_name, created_by, updated_by)
VALUES (1, 'My Jewellery Shop', 'system', 'system');
