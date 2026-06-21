-- Align the properties table with the embedded value objects mapped by PropertyJpaEntity.
-- Safe additive migration: existing nullable columns remain untouched for backward compatibility.

ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS street_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS total_area DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS occupied_area DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS unit_count INTEGER;
