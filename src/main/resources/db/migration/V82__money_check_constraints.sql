-- V82__money_check_constraints.sql
--
-- No money column anywhere had a CHECK constraint. Every bound added below
-- already matches an invariant the domain model enforces in Java (see
-- RentTransaction.create, RentLedgerEntry.create/applyTransaction,
-- Deposit.create, RentPaymentRequest.create) EXCEPT disbursements.amount,
-- which Disbursement.create never validates at all — this is a genuinely
-- new safety net there, not just defense-in-depth.

ALTER TABLE rent_transactions
    ADD CONSTRAINT ck_rent_transactions_amount_positive CHECK (amount > 0);

ALTER TABLE rent_ledger_entries
    ADD CONSTRAINT ck_rent_ledger_entries_amount_due_positive CHECK (amount_due > 0),
    ADD CONSTRAINT ck_rent_ledger_entries_amount_paid_non_negative CHECK (amount_paid >= 0);

ALTER TABLE deposits
    ADD CONSTRAINT ck_deposits_amount_required_positive CHECK (amount_required > 0),
    ADD CONSTRAINT ck_deposits_amount_paid_non_negative CHECK (amount_paid >= 0),
    ADD CONSTRAINT ck_deposits_amount_refunded_non_negative CHECK (amount_refunded >= 0);

ALTER TABLE disbursements
    ADD CONSTRAINT ck_disbursements_amount_positive CHECK (amount > 0);

ALTER TABLE rent_payment_requests
    ADD CONSTRAINT ck_rent_payment_requests_amount_positive CHECK (amount > 0);
