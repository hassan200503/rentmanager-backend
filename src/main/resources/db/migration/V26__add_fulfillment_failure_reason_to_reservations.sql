-- V26__add_fulfillment_failure_reason_to_reservations.sql

ALTER TABLE reservations
    ADD COLUMN fulfillment_failure_reason VARCHAR(2000);