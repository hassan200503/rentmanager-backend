-- Adds the four branding columns the Tenant entity has always mapped and no
-- migration ever created.
--
-- Tenant.brandingSettings is an @Embedded BrandingSettings with explicit
-- @AttributeOverride column names: branding_logo_url, branding_favicon_url,
-- branding_primary_color, branding_secondary_color. None of them existed.
-- With spring.jpa.hibernate.ddl-auto: none, Hibernate never created them
-- either, so ANY JPA load of a Tenant failed:
--
--   ERROR: column t1_0.branding_favicon_url does not exist
--
-- That is not a cosmetic gap. Every path that reads a landlord from the
-- database was broken by it:
--
--   * PayoutDestinationController (GET and PUT) - the payout number page
--   * B2CDisbursementService - resolving the payout recipient
--   * UnitReservationTransactionService - loading the landlord's Daraja
--     credentials to take a reservation deposit
--   * RentPaymentInitiationService - since V89, resolving whose credentials
--     sign the rent prompt
--
-- It survived because almost every unit test mocks TenantRepository, so the
-- mapping was never exercised against a real schema. It surfaced when an
-- integration test began loading a Tenant through the EntityManager.
--
-- Nullable with no default: branding is optional, and BrandingSettings
-- already treats absent values as absent. Widths match the value object's
-- own validation - URLs are free-form, colours are validated as hex strings
-- by BrandingSettings.validateColor and never need more than a handful of
-- characters, but 32 leaves room for rgb()/rgba() forms without another
-- migration.

ALTER TABLE tenants ADD COLUMN branding_logo_url        VARCHAR(500);
ALTER TABLE tenants ADD COLUMN branding_favicon_url     VARCHAR(500);
ALTER TABLE tenants ADD COLUMN branding_primary_color   VARCHAR(32);
ALTER TABLE tenants ADD COLUMN branding_secondary_color VARCHAR(32);

COMMENT ON COLUMN tenants.branding_logo_url IS
    'Per-landlord branding. Mapped by Tenant.brandingSettings (@Embedded '
    'BrandingSettings). Added by V90 - the mapping predated the column.';
