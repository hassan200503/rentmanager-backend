-- V62__add_mixed_use_and_premises_override_audit.sql
--
-- Two changes to the premises classification governance:
--
-- 1) MIXED_USE becomes a legal third value of premises_type.
--    A single building let to both residential and commercial tenants must,
--    under the Income Tax Act (mixed-use FAQ), split its rental income between
--    the MRI regime (residential portion) and the standard income tax / 16%
--    VAT branch (commercial portion). A binary RESIDENTIAL/COMMERCIAL value
--    forces such a building into one regime and silently misclassifies the
--    other half. MIXED_USE can never be auto-derived from property_type (the
--    domain derivation rule stays binary); it only arrives through an explicit,
--    audited override (a reason is mandatory).
--
--    Behavior is fail-closed until unit-level classification (phase 2):
--    MIXED_USE properties are EXCLUDED from the MRI aggregation (the JPQL
--    already filters premises_type = RESIDENTIAL) and invoiced as VAT-exempt.
--
-- 2) Audit trail for premises classification overrides.
--    premises_type is a legal/tax attribute the platform's tax pipeline relies
--    on (MRI filings, VAT treatment). A silent user override that contradicts
--    the auto-derivation would make the platform generate filings under the
--    wrong regime. Every explicit override therefore carries:
--      * premises_type_override_reason -- mandatory free text explaining why
--        the user deviated from the automatic classification,
--      * premises_type_changed_by     -- authenticated user id,
--      * premises_type_changed_at     -- timestamp.
--    Auto-classified properties leave all three NULL.

ALTER TABLE properties
    DROP CONSTRAINT IF EXISTS ck_properties_premises_type;

UPDATE properties
SET premises_type = 'COMMERCIAL'
WHERE property_type IN ('COMMERCIAL', 'OFFICE', 'WAREHOUSE')
  AND premises_type IS DISTINCT FROM 'COMMERCIAL';

ALTER TABLE properties
    ADD CONSTRAINT ck_properties_premises_type
        CHECK (premises_type IN ('RESIDENTIAL', 'COMMERCIAL', 'MIXED_USE'));

ALTER TABLE properties
    ADD COLUMN premises_type_override_reason VARCHAR(500),
    ADD COLUMN premises_type_changed_by    UUID,
    ADD COLUMN premises_type_changed_at    TIMESTAMPTZ;