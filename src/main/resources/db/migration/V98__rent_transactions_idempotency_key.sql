-- V98: Idempotency for manually recorded rent transactions.
--
-- A landlord or caretaker recording a CASH payment sends no external
-- reference, so the V33 duplicate guard (unique tenant_id + external_reference)
-- never applied to them. Every POST /rent-ledger/entries/{id}/transactions
-- minted a fresh correlation id, so a double tap, or a retry after a mobile
-- network timeout whose first request had in fact succeeded, recorded the
-- same cash twice — and the append-only ledger (V81) means the fix is a
-- reversal, not a delete.
--
-- Clients now send an Idempotency-Key header per logical submission. The key
-- is stored on the transaction row and made unique per landlord organisation,
-- so the database itself refuses the second insert even when two requests
-- race. Nullable: every existing row and every system-generated transaction
-- (M-Pesa callbacks, B2C, reversals) has none.

ALTER TABLE rent_transactions
    ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uk_rent_transactions_tenant_idempotency_key
    ON rent_transactions (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Keep the append-only guard complete: the key is part of what a transaction
-- was when it was recorded and must never change afterwards.
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
            OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
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
