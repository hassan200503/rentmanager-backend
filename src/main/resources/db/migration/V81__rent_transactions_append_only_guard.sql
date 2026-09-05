-- V81__rent_transactions_append_only_guard.sql
--
-- RentTransaction's class javadoc says it's "Never updated, never deleted,
-- once persisted" but nothing enforced that at the DB level — the row
-- carries updated_at/version like any other mutable entity. One legitimate
-- exception already exists in application code: RentPaymentCallbackTransactionService
-- applies a commission snapshot (commission_rate_percent, commission_amount,
-- net_amount) in a second UPDATE after the initial INSERT, once the
-- landlord's active commission policy is resolved. Everything else about a
-- transaction — its type, amount, tenant, ledger entry, external reference,
-- who recorded it and when, and whether it reverses another transaction —
-- must never change after insert.
--
-- This trigger allows that one legitimate update path and blocks every
-- other mutation, including DELETE (the actual defect this closes: deleting
-- a row instead of reversing it, per V71's REVERSAL type, freed the M-Pesa
-- duplicate-callback guard).

CREATE OR REPLACE FUNCTION enforce_rent_transactions_append_only()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'rent_transactions is append-only: rows cannot be deleted (id=%). Post a REVERSAL instead.', OLD.id;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id
            OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
            OR NEW.ledger_entry_id IS DISTINCT FROM OLD.ledger_entry_id
            OR NEW.lease_id IS DISTINCT FROM OLD.lease_id
            OR NEW.type IS DISTINCT FROM OLD.type
            OR NEW.amount IS DISTINCT FROM OLD.amount
            OR NEW.external_reference IS DISTINCT FROM OLD.external_reference
            OR NEW.source IS DISTINCT FROM OLD.source
            OR NEW.recorded_by IS DISTINCT FROM OLD.recorded_by
            OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at
            OR NEW.reverses_transaction_id IS DISTINCT FROM OLD.reverses_transaction_id
            OR NEW.currency IS DISTINCT FROM OLD.currency
            OR NEW.created_at IS DISTINCT FROM OLD.created_at
        THEN
            RAISE EXCEPTION 'rent_transactions is append-only: only the commission snapshot (commission_rate_percent, commission_amount, net_amount) may be updated after insert (id=%)', OLD.id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rent_transactions_append_only
    BEFORE UPDATE OR DELETE ON rent_transactions
    FOR EACH ROW EXECUTE FUNCTION enforce_rent_transactions_append_only();
