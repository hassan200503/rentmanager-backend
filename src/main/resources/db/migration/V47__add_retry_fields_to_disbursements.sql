-- V47__add_retry_fields_to_disbursements.sql
--
-- Retry tracking for B2C disbursements. retry_count tracks how many times
-- the disbursement has been retried; requires_manual_attention is set when
-- retries are exhausted and human intervention is needed.

ALTER TABLE disbursements
    ADD COLUMN retry_count                INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN requires_manual_attention   BOOLEAN NOT NULL DEFAULT FALSE;
