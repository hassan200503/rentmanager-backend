-- V84__rent_reminders.sql
--
-- The rent reminder system had no memory. RentReminderScheduler sent one SMS
-- at T-3 and RentOverdueNotificationListener sent one on the overdue event,
-- and neither recorded that it had done so. A restart, a redeploy across the
-- 09:00 boundary, a manual trigger or a redelivered domain event all resend —
-- and a renter who gets the same "your rent is due" SMS three times trusts
-- the next one less, not more.
--
-- This table is the reminder system's memory. It is deliberately NOT a money
-- table: it stores no balance the ledger doesn't already own, and nothing
-- reads it to decide what someone owes. rent_ledger_entries remains the sole
-- source of financial truth; this records only "we told this person about
-- that entry, at this milestone, on this channel, at this time".
--
-- balance_owed_snapshot is the one number here, and it is evidence, not
-- state: it is what the message actually said, so a dispute six months later
-- can be answered with the figure the renter was shown rather than the
-- figure the ledger holds today.
--
-- The unique index is the whole design. Idempotency is enforced by the
-- database, not by a code path that remembers to check first — the same
-- discipline as uk_rent_transactions_tenant_external_reference (V33). A
-- second attempt at the same (entry, milestone, audience, channel) raises a
-- constraint violation the sender catches and treats as "already sent".

CREATE TABLE rent_reminders
(
    id                       UUID           PRIMARY KEY,
    tenant_id                UUID           NOT NULL,
    rent_ledger_entry_id     UUID           NOT NULL,
    lease_id                 UUID           NOT NULL,

    -- Which point in the cadence this message represents.
    -- T_MINUS_7 | T_MINUS_3 | DUE_TODAY | OVERDUE_1 | OVERDUE_3 | OVERDUE_7
    milestone                VARCHAR(32)    NOT NULL,

    -- RENTER | LANDLORD. The same milestone legitimately produces one
    -- message to each, so audience is part of the dedupe key.
    audience                 VARCHAR(16)    NOT NULL,

    -- SMS | EMAIL | WHATSAPP | IN_APP
    channel                  VARCHAR(16)    NOT NULL,

    -- Masked at write time (PhoneMasker / email local-part). The unmasked
    -- recipient lives only on notification_deliveries, which is the row that
    -- actually needs it to send. An audit log does not need to be a second
    -- copy of everyone's phone number.
    recipient_masked         VARCHAR(255)   NOT NULL,

    -- What the message said, not what is owed now. See header note.
    balance_owed_snapshot    NUMERIC(19, 2) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL DEFAULT 'KES',
    due_date                 DATE           NOT NULL,

    -- Links to the outbox row that carries retry/backoff. Nullable because
    -- the reminder record is written in the same transaction that enqueues,
    -- and a channel may be delivered inline in future without an outbox row.
    notification_delivery_id UUID,

    sent_at                  TIMESTAMPTZ    NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_rent_reminders_balance_non_negative
        CHECK (balance_owed_snapshot >= 0),

    CONSTRAINT fk_rent_reminders_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),

    CONSTRAINT fk_rent_reminders_ledger_entry
        FOREIGN KEY (rent_ledger_entry_id) REFERENCES rent_ledger_entries (id)
);

-- The idempotency guard. One message per entry, per milestone, per audience,
-- per channel — forever. tenant_id leads the key to keep every read of this
-- table tenant-scoped by construction, matching the convention on every
-- other tenant-owned table.
CREATE UNIQUE INDEX uk_rent_reminders_entry_milestone_audience_channel
    ON rent_reminders (tenant_id, rent_ledger_entry_id, milestone, audience, channel);

-- Sweep support: "what has this entry already received", the query the
-- scheduler runs for every candidate.
CREATE INDEX idx_rent_reminders_entry
    ON rent_reminders (rent_ledger_entry_id);

-- Landlord-facing reporting: "what did we send this month, and what did it
-- cost me in SMS".
CREATE INDEX idx_rent_reminders_tenant_sent_at
    ON rent_reminders (tenant_id, sent_at DESC);

-- Append-only, for the same reason rent_transactions is (V81): a reminder
-- log that can be edited afterwards is worthless as evidence. There is no
-- legitimate update path at all here — unlike rent_transactions, which has
-- the commission snapshot — so this guard is absolute.
CREATE OR REPLACE FUNCTION enforce_rent_reminders_append_only()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'rent_reminders is append-only: rows cannot be deleted (id=%)', OLD.id;
    END IF;

    RAISE EXCEPTION 'rent_reminders is append-only: rows cannot be updated (id=%)', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rent_reminders_append_only
    BEFORE UPDATE OR DELETE ON rent_reminders
    FOR EACH ROW EXECUTE FUNCTION enforce_rent_reminders_append_only();
