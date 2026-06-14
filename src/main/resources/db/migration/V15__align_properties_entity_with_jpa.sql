-- =========================================================
-- V15: Align properties table with Hibernate entity mapping
-- Safe additive migration (no destructive operations)
-- =========================================================

-- 1. Add missing enum/type column used by JPA
ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS property_type VARCHAR(50);

-- 2. Add occupancy status (if your entity uses it)
ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS occupancy_status VARCHAR(50);

-- 3. Ensure reference_code uniqueness exists (safe guard)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'properties_reference_code_key'
    ) THEN
ALTER TABLE properties
    ADD CONSTRAINT properties_reference_code_key UNIQUE (reference_code);
END IF;
END $$;

-- 4. Ensure tenant index exists (performance + multi-tenancy correctness)
CREATE INDEX IF NOT EXISTS idx_property_tenant
    ON properties(tenant_id);

-- 5. Ensure status index exists (workflow queries)
CREATE INDEX IF NOT EXISTS idx_property_status
    ON properties(status);

-- 6. Ensure reference_code index exists
CREATE INDEX IF NOT EXISTS idx_property_reference_code
    ON properties(reference_code);

-- 7. Backfill safety (optional but stable defaulting)
UPDATE properties
SET property_type = 'APARTMENT'
WHERE property_type IS NULL;

UPDATE properties
SET occupancy_status = 'VACANT'
WHERE occupancy_status IS NULL;