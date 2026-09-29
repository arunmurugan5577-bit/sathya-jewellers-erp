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
