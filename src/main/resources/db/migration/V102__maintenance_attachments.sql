-- V102: Photos attached to maintenance requests.
--
-- A photo of a leak or a broken socket is often the whole report. These are
-- pictures taken inside someone's home, so they are stored as PRIVATE
-- ("authenticated") Cloudinary assets and served only through the API, which
-- authorises every view: the landlord organisation that owns the request, or
-- the renter who reported it. No public or shareable URL is ever issued.

CREATE TABLE maintenance_attachments (
    id                       UUID         NOT NULL,
    tenant_id                UUID         NOT NULL,
    maintenance_request_id   UUID         NOT NULL,
    storage_key              VARCHAR(255) NOT NULL,
    content_type             VARCHAR(32)  NOT NULL,
    size_bytes               INTEGER      NOT NULL,
    uploaded_by              VARCHAR(16)  NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_maintenance_attachments PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_attachments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_maintenance_attachments_request FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_requests (id),
    CONSTRAINT ck_maintenance_attachments_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_maintenance_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_maintenance_attachments_uploader CHECK (uploaded_by IN ('RENTER', 'LANDLORD'))
);

CREATE INDEX idx_maintenance_attachments_request ON maintenance_attachments (tenant_id, maintenance_request_id, created_at);
