CREATE TABLE activity_log (
                              id           UUID PRIMARY KEY,
                              tenant_id    UUID NOT NULL,
                              event_type   TEXT NOT NULL,        -- e.g. 'PROPERTY_CREATED', 'PROPERTY_ARCHIVED'
                              entity_type  TEXT NOT NULL,        -- e.g. 'Property', 'Unit'
                              entity_id    UUID NOT NULL,
                              entity_name  TEXT NOT NULL,        -- denormalized label, e.g. 'Sunset Apartments'
                              actor_id     UUID,
                              actor_name   TEXT NOT NULL,
                              metadata     TEXT,                 -- JSON-encoded string; kept as TEXT to avoid jsonb cast issues without a JSON library
                              created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every dashboard query filters by tenant and orders by recency.
CREATE INDEX idx_activity_log_tenant_created ON activity_log (tenant_id, created_at DESC);