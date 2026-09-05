-- V86__audit_logs.sql
--
-- The audit module has existed since early in the project as a complete set
-- of shapes with nothing behind them: a domain model, a service interface, a
-- default service, an event listener, a config class — and a repository whose
-- save() reads
--
--     // JPA implementation later
--     return auditLog;
--
-- It persists nothing. There is no entity, no table, and no migration. Every
-- audit call ever made in this system has been written to nowhere, including
-- the one live caller (PlatformSettingsService). The module reported success
-- the whole time, which is the worst way for an audit trail to fail.
--
-- This creates the table it always assumed.
--
-- Why it matters more than the other stubs: rent_transactions is append-only
-- (V81) and proves THAT money moved and that the row was never edited. It has
-- no idea WHO authorised it, from where, or against what balance. In a
-- dispute — and rent collection produces disputes — that is the only question
-- anyone asks.

CREATE TABLE audit_logs
(
    id             UUID         PRIMARY KEY,

    -- Nullable: platform-level actions (a platform admin changing a global
    -- setting) belong to no landlord organisation. Everything tenant-scoped
    -- must set it.
    tenant_id      UUID,

    action         VARCHAR(64)  NOT NULL,

    -- Who did it. actor_id is the Clerk user id rather than the local UUID,
    -- because that is the identity the JWT actually carries and the one that
    -- survives a local user row being replaced. 'SYSTEM' for scheduler work.
    actor_id       VARCHAR(255) NOT NULL,
    actor_type     VARCHAR(32)  NOT NULL,

    entity_type    VARCHAR(64),
    entity_id      VARCHAR(64),

    -- Ties an audit row to the ledger correlation id already threaded through
    -- the money paths, so a payout can be traced back to the payment that
    -- funded it.
    correlation_id VARCHAR(255),

    status         VARCHAR(16)  NOT NULL,

    -- JSON. Holds the before/after of whatever changed. Deliberately text
    -- rather than JSONB: nothing queries inside it today, and a column that
    -- promises structure invites someone to depend on a shape no constraint
    -- enforces.
    metadata       TEXT,

    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_audit_logs_status
        CHECK (status IN ('SUCCESS', 'FAILED'))
);

-- "What happened on this account, most recent first" — the query a support
-- conversation or a dispute starts with.
CREATE INDEX idx_audit_logs_tenant_created_at
    ON audit_logs (tenant_id, created_at DESC);

-- "Everything that touched this disbursement / ledger entry / lease."
CREATE INDEX idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id);

-- "Everything that happened under this correlation id", across modules.
CREATE INDEX idx_audit_logs_correlation
    ON audit_logs (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- "What has this user been doing", the question an investigation asks.
CREATE INDEX idx_audit_logs_actor
    ON audit_logs (actor_id, created_at DESC);

-- Append-only, absolutely. rent_transactions needed one narrow update path
-- for its commission snapshot (V81); an audit log has none at all. A record
-- of who authorised a payout is worth exactly nothing if the person who
-- authorised it can edit or delete the row afterwards.
CREATE OR REPLACE FUNCTION enforce_audit_logs_append_only()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_logs is append-only: rows cannot be deleted (id=%)', OLD.id;
    END IF;

    RAISE EXCEPTION 'audit_logs is append-only: rows cannot be updated (id=%)', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION enforce_audit_logs_append_only();
