-- V52__subscription_pending_payment_guard.sql
--
-- DB-level guarantee that a tenant/purpose can never have two PENDING
-- subscription payment requests at once, even under concurrent retry
-- clicks (the app-level check-then-create in switchToPremium is not
-- atomic). The loser of a race gets a constraint violation (surfaced as
-- a 409 ALREADY_PENDING), so a landlord can never end up with two live
-- STK pushes / double charges from double-tapping "Pay & switch".
CREATE UNIQUE INDEX uk_sub_payment_requests_pending
    ON subscription_payment_requests (tenant_id, purpose)
    WHERE status = 'PENDING';
