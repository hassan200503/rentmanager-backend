-- V56__add_kra_tax_columns.sql
--
-- Build Brief v2 (Part A) — eTIMS / eRITS tax-compliance foundation.
--
-- 1. tenants.kra_pin: the landlord's KRA PIN. Required before any eTIMS
--    transmission or eRITS filing can be executed for this landlord. Kept
--    optional at the DB level so onboarding never hard-blocks on it.
-- 2. tenants.vat_registered: landlord VAT-registration status. Drives the
--    commercial-rent 16% VAT invoice branch (First Schedule Part II para 8
--    of the VAT Act 2013 exempts residential premises only; commercial rent
--    has been chargeable since 1 Jan 2008). Fail-closed FALSE — VAT is only
--    charged when the landlord is confirmed VAT-registered. The exact 16%
--    branch and registration thresholds still need a tax advisor's sign-off
--    before the live path is enabled (see A1).
-- 3. tenant_profile.kra_pin: the renter's KRA PIN. At the eTIMS invoice
--    level this is optional/conditional (only needed when the renter claims
--    the expense/input VAT). It is distinct from the eRITS-level tenant PIN
--    that KRA expects at property registration (PropertyTaxRegistration),
--    which is treated as expected-but-not-yet-mandatory (see A1).

ALTER TABLE tenants
    ADD COLUMN kra_pin VARCHAR(30);

ALTER TABLE tenants
    ADD COLUMN vat_registered BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_profile
    ADD COLUMN kra_pin VARCHAR(30);