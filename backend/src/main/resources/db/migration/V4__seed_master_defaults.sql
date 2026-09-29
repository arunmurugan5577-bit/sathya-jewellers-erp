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
