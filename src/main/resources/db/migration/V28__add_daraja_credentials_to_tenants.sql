-- V24__add_daraja_credentials_to_tenants.sql

ALTER TABLE tenants
    ADD COLUMN daraja_consumer_key VARCHAR(500),
    ADD COLUMN daraja_consumer_secret VARCHAR(500),
    ADD COLUMN daraja_business_short_code VARCHAR(500),
    ADD COLUMN daraja_passkey VARCHAR(500),
    ADD COLUMN daraja_configured BOOLEAN NOT NULL DEFAULT FALSE;