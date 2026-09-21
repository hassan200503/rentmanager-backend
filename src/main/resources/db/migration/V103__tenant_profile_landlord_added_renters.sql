-- V103: let a landlord record a renter who has no account yet.
--
-- Until now the ONLY production code that created a tenant_profile row was
-- ReservationFulfillmentOrchestrator: a stranger reserving a vacant unit on the
-- public site and paying a deposit by M-Pesa. A landlord signing up with tenants
-- already living in their units had no way to enter them, so no lease, ledger,
-- payment or renter portal could exist for an existing tenancy. That is the
-- normal case in Kenya, which made the product unusable for it.
--
-- Two columns blocked it:
--   clerk_user_id NOT NULL — a renter added by their landlord has no Clerk
--     account yet, and may never create one (a caretaker collects cash).
--   email NOT NULL — many renters give only a phone number.
--
-- Both become nullable. A row with clerk_user_id IS NULL is an "unlinked"
-- renter: a real tenancy the landlord manages, which links to a Clerk identity
-- the first time that person signs in with a matching verified email
-- (RenterIdentityLinker).
--
-- Account deletion sets clerk_user_id to a non-null tombstone
-- (TenantProfile.unlinkIdentity), so an erased renter never re-enters the
-- unlinked set and cannot be re-linked by email.

ALTER TABLE tenant_profile ALTER COLUMN clerk_user_id DROP NOT NULL;
ALTER TABLE tenant_profile ALTER COLUMN email DROP NOT NULL;

-- One tenancy record per phone per landlord, while unlinked. Adding the same
-- renter twice would split their rent history across two profiles — the kind of
-- duplicate nobody notices until a balance is wrong. Postgres treats NULLs as
-- distinct, so the existing (tenant_id, clerk_user_id) unique index already
-- tolerates many unlinked rows; these two indexes are what actually prevent
-- duplicates among them.
CREATE UNIQUE INDEX idx_tenant_profile_pending_phone
    ON tenant_profile (tenant_id, phone)
    WHERE clerk_user_id IS NULL;

CREATE UNIQUE INDEX idx_tenant_profile_pending_email
    ON tenant_profile (tenant_id, LOWER(email))
    WHERE clerk_user_id IS NULL AND email IS NOT NULL;

-- Linking looks renters up by verified email across landlords (one person may
-- rent from several), and only ever considers unlinked rows.
CREATE INDEX idx_tenant_profile_unlinked_email
    ON tenant_profile (LOWER(email))
    WHERE clerk_user_id IS NULL AND email IS NOT NULL;
