ALTER TABLE tenants
ADD COLUMN logo_url VARCHAR(255),
ADD COLUMN favicon_url VARCHAR(255),
ADD COLUMN primary_color VARCHAR(50),
ADD COLUMN secondary_color VARCHAR(50);