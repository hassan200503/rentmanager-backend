-- V67__add_platform_logo_branding.sql
--
-- System-wide brand identity for the platform owner.
--
--   logo_url - Cloudinary secure URL of the platform logo, uploaded by the
--              platform OWNER from the admin console. Served unauthenticated
--              to every surface (console sidebar, landlord/renter shells,
--              landing page, favicon, email templates) via
--              GET /api/v1/public/platform/branding.
--
-- The asset lifecycle is owned by two owner-only endpoints:
--   POST   /api/v1/admin/settings/logo  (multipart upload, replaces + purges old asset)
--   DELETE /api/v1/admin/settings/logo  (removes branding + purges asset)
-- The generic settings PUT never touches the logo.

ALTER TABLE platform_settings
    ADD COLUMN logo_url VARCHAR(500);