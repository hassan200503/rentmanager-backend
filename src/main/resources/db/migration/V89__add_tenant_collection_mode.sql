-- Separates "does the platform hold the money" from "how does the platform
-- earn", and makes non-custodial collection the default.
--
-- Until now the only switch was billing_mode (COMMISSION | PREMIUM_MONTHLY),
-- which conflated the two. COMMISSION was the DEFAULT (V50), so every
-- landlord was implicitly opted into the platform collecting their rent,
-- deducting a cut, and disbursing the remainder by B2C.
--
-- That is payment aggregation. Safaricom's M-PESA terms cl. 15.2(l) prohibit
-- it without written consent; it requires CBK authorisation under the NPS Act
-- 2011 (Electronic Retail PSP, KSh 5m core capital); and B2C additionally
-- requires a pre-funded trust account (cl. 6.1(a)). The backend CLAUDE.md has
-- said since it was written that commission mode must not be the default
-- until a licence path is decided. This migration makes the code agree.
--
-- DIRECT signs the rent STK push with the landlord's own Daraja credentials,
-- so the money settles into their own paybill and the platform never receives
-- it. The reservation/deposit flow has always worked this way; this brings
-- rent onto the same path.

ALTER TABLE tenants
    ADD COLUMN collection_mode VARCHAR(20) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE tenants
    ADD CONSTRAINT ck_tenants_collection_mode
        CHECK (collection_mode IN ('DIRECT', 'PLATFORM_CUSTODY'));

-- Existing rows move to DIRECT, deliberately.
--
-- The alternative — preserving today's behaviour by setting existing rows to
-- PLATFORM_CUSTODY — would carry the unlicensed arrangement forward silently,
-- which is the thing being fixed. A landlord on DIRECT without credentials
-- gets a clear refusal at payment time telling them to finish setup. That is
-- a support conversation. Continuing to aggregate is a regulatory exposure.
UPDATE tenants SET collection_mode = 'DIRECT';

COMMENT ON COLUMN tenants.collection_mode IS
    'DIRECT: rent is signed with the landlord''s own Daraja credentials and '
    'settles to their own paybill; the platform never holds it. '
    'PLATFORM_CUSTODY: legacy aggregation - rent lands in the platform '
    'paybill, commission is deducted, net is disbursed by B2C. '
    'PLATFORM_CUSTODY REQUIRES CBK AUTHORISATION AND SAFARICOM WRITTEN '
    'CONSENT before use with real money. Never make it the default.';

CREATE INDEX idx_tenants_collection_mode ON tenants (collection_mode);
