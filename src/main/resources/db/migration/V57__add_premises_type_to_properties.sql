-- V57__add_premises_type_to_properties.sql
--
-- Adds the RESIDENTIAL/COMMERCIAL classification to properties.
--
-- The existing PropertyType enum is a flat list (APARTMENT, BEDSITTER,
-- STUDIO, MAISONETTE, VILLA, COMMERCIAL, OFFICE, WAREHOUSE, HOSTEL,
-- AIRBNB) with no grouping. This new column is the single source of truth
-- for the tax branch:
--   * RESIDENTIAL -> MRI regime (VAT-exempt rental receipt; monthly
--     aggregation for MonthlyRentalIncomeFiling must include only these).
--   * COMMERCIAL  -> never in the MRI regime (Finance Act 2023 wording);
--     VAT applies when the landlord is VAT-registered.
--
-- Existing rows are backfilled from property_type; new rows default based
-- on their property type via the domain layer (Property.getPremisesType()
-- falls back to deriving from PropertyType), and the column is then NOT
-- NULL. The CHECK constraint locks the legal enumerable values.

ALTER TABLE properties
    ADD COLUMN premises_type VARCHAR(30);

UPDATE properties
SET premises_type = CASE
    WHEN property_type IN ('COMMERCIAL', 'OFFICE', 'WAREHOUSE')
        THEN 'COMMERCIAL'
    ELSE 'RESIDENTIAL'
END;

ALTER TABLE properties
    ALTER COLUMN premises_type SET NOT NULL;

ALTER TABLE properties
    ADD CONSTRAINT ck_properties_premises_type
        CHECK (premises_type IN ('RESIDENTIAL', 'COMMERCIAL'));