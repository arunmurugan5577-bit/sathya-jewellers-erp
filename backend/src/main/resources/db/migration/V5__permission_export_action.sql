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
