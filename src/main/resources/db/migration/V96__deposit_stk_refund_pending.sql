-- Tracks an in-flight STK push refund authorisation. When the landlord
-- initiates a deposit refund via M-Pesa prompt, these columns hold the
-- pending state until Safaricom's callback confirms or the flow fails.
-- All columns are nullable; only populated between initiation and completion.
ALTER TABLE deposits
    ADD COLUMN pending_refund_checkout_request_id VARCHAR(100),
    ADD COLUMN pending_refund_phone               VARCHAR(20),
    ADD COLUMN pending_refund_deduction            NUMERIC(19, 2),
    ADD COLUMN pending_refund_deduction_reason     TEXT,
    ADD COLUMN pending_refund_remarks              TEXT,
    ADD COLUMN pending_refund_initiated_at         TIMESTAMP;

CREATE INDEX idx_deposit_pending_refund_crid
    ON deposits (pending_refund_checkout_request_id)
    WHERE pending_refund_checkout_request_id IS NOT NULL;
