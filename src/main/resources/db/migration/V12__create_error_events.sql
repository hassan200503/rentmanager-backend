CREATE TABLE error_events (
                              id UUID PRIMARY KEY,
                              error_code VARCHAR(100),
                              error_type VARCHAR(100),
                              http_method VARCHAR(20),
                              message TEXT,
                              metadata_json TEXT,
                              module VARCHAR(100),
                              path VARCHAR(255),
                              tenant_id UUID,
                              timestamp TIMESTAMP,
                              trace_id VARCHAR(100),
                              user_id UUID
);