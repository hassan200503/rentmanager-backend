-- V71__rent_transactions_reversal.sql
--
-- rent_transactions is meant to be append-only (RentTransaction's class
-- javadoc says so explicitly), but the DELETE /api/v1/rent-ledger/transactions/{id}
-- endpoint used to hard-delete the row via RentLedgerApplicationService
-- .deleteTransaction(). That destroyed financial history AND freed
-- uk_rent_transactions_tenant_external_reference (the M-Pesa duplicate-
-- callback guard added in V33), so the same receipt could be replayed and
-- applied a second time after a "delete".
--
-- Replaced with a reversal: the original row is kept, and a new
-- rent_transactions row (type REVERSAL) is posted referencing it via
-- reverses_transaction_id, carrying no external_reference of its own so the
-- original's still occupies the uniqueness guard.

ALTER TABLE rent_transactions
    ADD COLUMN reverses_transaction_id UUID REFERENCES rent_transactions (id);

-- At most one reversal per original transaction.
CREATE UNIQUE INDEX uk_rent_transactions_reverses_transaction_id
    ON rent_transactions (reverses_transaction_id)
    WHERE reverses_transaction_id IS NOT NULL;
