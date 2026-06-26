-- V{next}__create_property_media_and_unit_media.sql
-- =====================================================
-- PROPERTY MEDIA
-- =====================================================
CREATE TABLE property_media (
                                id              UUID        NOT NULL,
                                tenant_id       UUID        NOT NULL,
                                property_id     UUID        NOT NULL,
                                file_name       VARCHAR(255) NOT NULL,
                                file_url        VARCHAR(1000) NOT NULL,
                                public_id       VARCHAR(255) NOT NULL,          -- Cloudinary public_id (required for delete/replace)
                                content_type    VARCHAR(100) NOT NULL,
                                file_size       BIGINT      NOT NULL,
                                primary_media   BOOLEAN     NOT NULL DEFAULT FALSE,
                                uploaded_at     TIMESTAMPTZ NOT NULL,
                                version         BIGINT      NOT NULL DEFAULT 0,

                                CONSTRAINT pk_property_media PRIMARY KEY (id)
);

CREATE INDEX idx_property_media_tenant   ON property_media (tenant_id);
CREATE INDEX idx_property_media_property ON property_media (property_id);

-- =====================================================
-- UNIT MEDIA
-- =====================================================
CREATE TABLE unit_media (
                            id            UUID         NOT NULL,
                            tenant_id     UUID         NOT NULL,
                            unit_id       UUID         NOT NULL,
                            url           VARCHAR(1000) NOT NULL,
                            type          VARCHAR(50)  NOT NULL,        -- IMAGE, VIDEO, DOCUMENT
                            caption       VARCHAR(500),
                            primary_media BOOLEAN      NOT NULL DEFAULT FALSE,
                            sort_order    INT          NOT NULL DEFAULT 0,
                            version       BIGINT       NOT NULL DEFAULT 0,

                            CONSTRAINT pk_unit_media PRIMARY KEY (id)
);

CREATE INDEX idx_unit_media_tenant ON unit_media (tenant_id);
CREATE INDEX idx_unit_media_unit   ON unit_media (unit_id);