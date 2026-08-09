-- V65: Bidirectional platform reviews + moderation workflow.
--
-- 1. landlord_reviews (renter -> landlord, pre-existing) gains a moderation
--    status. Rows that existed before moderation are backfilled APPROVED:
--    they were already published. New submissions enter PENDING and only
--    reach public surfaces once a platform admin approves them.
-- 2. renter_reviews (landlord -> renter, new) mirrors the shape, so both
--    sides of a tenancy can rate each other and feed the public platform
--    testimonials endpoint.

ALTER TABLE landlord_reviews
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE landlord_reviews
    ADD CONSTRAINT ck_landlord_reviews_status
        CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN'));

CREATE INDEX idx_landlord_reviews_tenant_status
    ON landlord_reviews (tenant_id, status);

CREATE INDEX idx_landlord_reviews_status_created
    ON landlord_reviews (status, created_at);

CREATE TABLE renter_reviews (
    id                UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    tenant_profile_id UUID         NOT NULL,
    lease_id          UUID         NOT NULL,
    rating            SMALLINT     NOT NULL,
    comment           VARCHAR(1000),
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_renter_reviews PRIMARY KEY (id),
    CONSTRAINT fk_renter_reviews_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_renter_reviews_tenant_profile
        FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profile (id),
    CONSTRAINT fk_renter_reviews_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id),
    CONSTRAINT ck_renter_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_renter_reviews_status
        CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN')),
    CONSTRAINT uq_renter_reviews_tenant UNIQUE (tenant_id, tenant_profile_id)
);

CREATE INDEX idx_renter_reviews_tenant_status
    ON renter_reviews (tenant_id, status);

CREATE INDEX idx_renter_reviews_status_created
    ON renter_reviews (status, created_at);
