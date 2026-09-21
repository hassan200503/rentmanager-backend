-- V104: keep the platform icon in our own database.
--
-- Uploading a logo went to Cloudinary (MediaUploadService.uploadPlatformBrandAsset)
-- and stored the returned URL in platform_settings.logo_url. On a deployment
-- with no Cloudinary credentials — the free-tier default, since photo storage
-- is configured later or never — the upload simply failed, so the owner could
-- not change the icon at all.
--
-- A brand icon is a few kilobytes and changes once a year; it does not need a
-- CDN, and holding it here means one upload works on day one with no third
-- party account. Property and repair photos stay on Cloudinary: those are
-- many, large, and belong in object storage.
--
-- Its own single-row table rather than more columns on platform_settings: the
-- icon is written by a multipart endpoint, read by an unauthenticated image
-- endpoint, and has nothing to do with the operational settings that row
-- carries. platform_settings.logo_url is left untouched, so a deployment that
-- already uploaded to Cloudinary keeps working; the bytes here win when both
-- exist.

CREATE TABLE platform_brand_icon (
    -- One icon for the platform. The CHECK pins the row id so a second row
    -- cannot be inserted: "which icon is current?" must never be a question.
    id           SMALLINT     PRIMARY KEY DEFAULT 1 CONSTRAINT chk_brand_icon_single_row CHECK (id = 1),
    content_type VARCHAR(100) NOT NULL,
    bytes        BYTEA        NOT NULL,
    byte_size    INTEGER      NOT NULL,
    updated_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Only known-safe raster types are ever stored. SVG is deliberately
    -- excluded: it is a document that can carry script, and this file is
    -- served from our own origin to every visitor.
    CONSTRAINT chk_brand_icon_content_type
        CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),

    -- Matches the application's own limit. A ceiling in the schema too, because
    -- this row is read on nearly every page load.
    CONSTRAINT chk_brand_icon_size
        CHECK (byte_size > 0 AND byte_size <= 524288)
);
