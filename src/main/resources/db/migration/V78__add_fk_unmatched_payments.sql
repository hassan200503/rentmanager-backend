-- V78__add_fk_unmatched_payments.sql
--
-- resolved_unit_id is nullable — populated only once an admin resolves the
-- unmatched payment to a specific unit.

ALTER TABLE unmatched_payments
    ADD CONSTRAINT fk_unmatched_payments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE unmatched_payments
    ADD CONSTRAINT fk_unmatched_payments_resolved_unit
        FOREIGN KEY (resolved_unit_id) REFERENCES units (id);
