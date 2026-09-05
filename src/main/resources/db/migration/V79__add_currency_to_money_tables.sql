-- V79__add_currency_to_money_tables.sql
--
-- Every money-bearing table stored a bare NUMERIC(19,2) with no currency
-- attached, even though tenants.currency already exists. Kenya-first system,
-- so 'KES' is the correct default for existing rows and for any write path
-- not yet updated to pass the landlord's actual tenant.currency explicitly.

ALTER TABLE rent_ledger_entries
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';

ALTER TABLE rent_transactions
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';

ALTER TABLE disbursements
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';

ALTER TABLE rent_payment_requests
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';

ALTER TABLE deposits
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';
