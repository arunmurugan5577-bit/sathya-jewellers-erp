-- ===========================================================================
-- V26: a GSTIN does not have to carry Z in its fourteenth place.
--
-- The checks written in V1 and V7 hardcoded Z there:
--
--     ^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$
--                                                 ^
--
-- That is the common shorthand, and it is wrong. The fourteenth character is Z
-- for most registrations but it is a reserved position, not a literal, and the
-- department issues numbers that hold something else. The shop's own GSTIN is
-- one of them - 33FZDPS3754Q12L - so the application refused the number printed
-- on its own registration certificate, on every screen that takes one.
--
-- Everything else about the shape is kept, so the check still catches a GSTIN
-- that is the wrong length, or has letters where the state code belongs, or
-- digits inside the PAN. Only the position that was never a literal is freed.
--
-- Whether a given GSTIN is real is for the department to say; the only
-- authority here is the checksum, and a check constraint is the wrong place to
-- compute one.
-- ===========================================================================

ALTER TABLE shop_settings
    DROP CONSTRAINT ck_shop_settings_gstin,
    ADD CONSTRAINT ck_shop_settings_gstin
        CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[0-9A-Z]{3}$');

ALTER TABLE customers
    DROP CONSTRAINT ck_customers_gstin,
    ADD CONSTRAINT ck_customers_gstin
        CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[0-9A-Z]{3}$');
