-- V99: Expo push tickets awaiting a delivery receipt.
--
-- Expo accepts a message with a "ticket" immediately, but only reports whether
-- APNs/FCM actually delivered it later, via a receipt fetched by ticket id.
-- DeviceNotRegistered (app uninstalled, token rotated) most often arrives there,
-- not on the ticket. Without polling receipts the backend keeps sending to dead
-- devices indefinitely. Rows are short-lived: removed once a receipt is read,
-- and abandoned after 24h, which is how long Expo retains receipts.

CREATE TABLE push_tickets (
    ticket_id   VARCHAR(64)  NOT NULL,
    push_token  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_push_tickets PRIMARY KEY (ticket_id)
);

CREATE INDEX idx_push_tickets_created_at ON push_tickets (created_at);
