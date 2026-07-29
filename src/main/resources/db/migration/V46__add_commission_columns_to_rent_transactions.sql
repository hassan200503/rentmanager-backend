-- V46__add_commission_columns_to_rent_transactions.sql
--
-- Commission snapshot columns on rent_transactions. Populated only for
-- MPESA rent payments that go through the commission-split pipeline;
-- NULL for all other transaction types and historical records.

ALTER TABLE rent_transactions
    ADD COLUMN commission_rate_percent NUMERIC(5, 2),
    ADD COLUMN commission_amount      NUMERIC(19, 2),
    ADD COLUMN net_amount             NUMERIC(19, 2);
