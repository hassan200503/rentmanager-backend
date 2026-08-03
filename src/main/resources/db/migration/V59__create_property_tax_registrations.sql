-- V59__create_property_tax_registrations.sql
--
-- eRITS (Landlord-side regime): each landlord's residential properties must
-- be registered with KRA's eRITS before the landlord can be placed under the
-- Rental Income Tax (RIT) regime. This table is the property-level
-- registration record.
--
--   * landlord_kra_pin: the landlord's PIN (denormalised from tenants.kra_pin
--     at creation time so the registration survives later tenant edits).
--   * tenant_kra_pin: the KRA-pin KRA expects for the owner at the room in
--     their eRITS schema. At the DB level it is optional: we only populate it
--     for landlords who have given us their PIN. A tax advisor should confirm
--     whether eRITS hard-requires this before the registration transmission
--     is enabled (brief item A1).
--   * kr_property_registration_id: KRA-side acknowledgement when the
--     registration is accepted.
--
-- More realistically KRA also expects a property-specific identifier per room:
-- units carry a floor/room conception; while unit-level identity and any
-- per-unit registration mapping is Phase 3 (whether KRA identifiers rooms by
-- unit, or by lease, is an open question for KRA's schema).

CREATE TABLE property_tax_registrations (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL REFERENCES tenants (id),
    property_id                 UUID NOT NULL REFERENCES properties (id),
    landlord_kra_pin            VARCHAR(30),
    tenant_kra_pin              VARCHAR(30),
    kr_property_registration_id VARCHAR(80),
    status                      VARCHAR(30) NOT NULL,
    registered_at               TIMESTAMPTZ,
    last_error                  VARCHAR(500),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_property_tax_registration
        UNIQUE (tenant_id, property_id),
    CONSTRAINT ck_property_tax_reg_status
        CHECK (status IN ('PENDING', 'READY_FOR_MANUAL', 'TRANSMITTED',
                          'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_property_tax_reg_status
    ON property_tax_registrations (tenant_id, status);