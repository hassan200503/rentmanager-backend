-- V85__rent_reminder_policies.sql
--
-- Which reminders a landlord sends, and on which channel, is a business
-- decision that belongs to the landlord — not a constant in a scheduler.
-- SMS costs real money per message in Kenya and the landlord pays it, so a
-- six-touchpoint cadence billed to them without their say is not a feature.
--
-- One row per (tenant, milestone) rather than a JSONB blob: it is the shape
-- the settings UI renders anyway (a grid of milestones against channels),
-- and it lets the database hold the invariants instead of trusting whatever
-- wrote the JSON.
--
-- Deliberately NOT included: quiet hours. The reminder sweep runs at 09:00
-- Africa/Nairobi, so there is no hour to be quiet about. Configuration for a
-- situation that cannot arise is just a field that will one day be wrong.
--
-- Deliberately NOT included: an in_app column. NotificationChannel has
-- SMS, EMAIL and WHATSAPP; there is no in-app channel to switch on yet. The
-- codebase already carries one dead boolean nobody writes (tenants.verified)
-- and does not need a second — the column arrives with the channel.

CREATE TABLE rent_reminder_policies
(
    -- Surrogate key rather than a composite (tenant_id, milestone) primary
    -- key: every other table in the schema is UUID-keyed, and matching that
    -- keeps the JPA entity free of @IdClass ceremony. The pair is still
    -- unique — see uk_rent_reminder_policies_tenant_milestone below.
    id              UUID        PRIMARY KEY,

    tenant_id       UUID        NOT NULL,

    -- T_MINUS_7 | T_MINUS_3 | DUE_TODAY | OVERDUE_1 | OVERDUE_3 | OVERDUE_7
    milestone       VARCHAR(32) NOT NULL,

    -- Master switch for this milestone. When false no channel fires,
    -- regardless of the channel flags below.
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,

    -- Channels for the RENTER at this milestone.
    sms_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    email_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    whatsapp_enabled BOOLEAN    NOT NULL DEFAULT FALSE,

    -- Whether the LANDLORD also gets told at this milestone. Distinct from
    -- the renter channels above: escalation is exactly the case where you
    -- want the landlord informed and the renter not spammed further.
    notify_landlord BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_rent_reminder_policies_tenant_milestone
        UNIQUE (tenant_id, milestone),

    CONSTRAINT fk_rent_reminder_policies_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,

    CONSTRAINT ck_rent_reminder_policies_milestone
        CHECK (milestone IN ('T_MINUS_7', 'T_MINUS_3', 'DUE_TODAY',
                             'OVERDUE_1', 'OVERDUE_3', 'OVERDUE_7'))
);

-- Seed every existing landlord with the default cadence.
--
-- The defaults are opinionated and cost-aware:
--   T_MINUS_7   off      — a week out, nobody has forgotten yet. Opt-in.
--   T_MINUS_3   email    — the useful nudge, free to send.
--   DUE_TODAY   email+SMS— the message most likely to produce a payment.
--   OVERDUE_1   email    — one day late is usually timing, not refusal.
--                          Do not spend an SMS, and do not alarm anyone.
--   OVERDUE_3   email+SMS— now it is a pattern. Escalate the channel.
--   OVERDUE_7   email+SMS+landlord — the landlord needs to know a week has
--                          passed; this is where a human decision starts.
INSERT INTO rent_reminder_policies
    (id, tenant_id, milestone, enabled, sms_enabled, email_enabled, whatsapp_enabled, notify_landlord)
SELECT gen_random_uuid(), t.id, m.milestone, m.enabled, m.sms, m.email, FALSE, m.landlord
FROM tenants t
CROSS JOIN (
    VALUES
        ('T_MINUS_7', FALSE, FALSE, TRUE,  FALSE),
        ('T_MINUS_3', TRUE,  FALSE, TRUE,  FALSE),
        ('DUE_TODAY', TRUE,  TRUE,  TRUE,  FALSE),
        ('OVERDUE_1', TRUE,  FALSE, TRUE,  FALSE),
        ('OVERDUE_3', TRUE,  TRUE,  TRUE,  FALSE),
        ('OVERDUE_7', TRUE,  TRUE,  TRUE,  TRUE)
) AS m(milestone, enabled, sms, email, landlord)
ON CONFLICT (tenant_id, milestone) DO NOTHING;
