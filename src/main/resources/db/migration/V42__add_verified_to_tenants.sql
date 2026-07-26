ALTER TABLE tenants ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT false;
UPDATE tenants SET verified = true;

