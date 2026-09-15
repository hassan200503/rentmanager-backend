-- V93: Add deduction and M-Pesa reference tracking to deposit refunds.
--
-- Deposits previously only tracked amountRefunded and status. Landlords
-- now record the specific deduction (damage, unpaid bills) and the
-- Safaricom transaction code for the manual M-Pesa transfer they made
-- to return the renter's money. This gives both parties a clean paper
-- trail and makes any later dispute resolvable from one record.
--
-- deduction_amount  - portion of the deposit kept by the landlord; 0 for
--                     a full refund, > 0 for a partial refund.
-- deduction_reason  - free-text explanation (required if deduction > 0).
-- refund_reference  - Safaricom M-Pesa confirmation code (required when
--                     money was actually sent, i.e. refund > 0).
-- refund_remarks    - optional landlord notes for the refund record.
--
-- Existing rows: deduction_amount defaults to 0 (no deduction assumed),
-- other columns nullable so they don't break already-REFUNDED records.

ALTER TABLE deposits
    ADD COLUMN IF NOT EXISTS deduction_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deduction_reason TEXT,
    ADD COLUMN IF NOT EXISTS refund_reference  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS refund_remarks    TEXT;
