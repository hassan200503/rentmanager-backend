
CREATE TABLE payment_intents (
                                 id                          UUID            NOT NULL,
                                 unit_id                     UUID            NOT NULL,
                                 property_id                 UUID            NOT NULL,
                                 form_data_json              TEXT            NOT NULL,
                                 mpesa_checkout_request_id   VARCHAR(255),
                                 deposit_amount              NUMERIC(19, 2)  NOT NULL,
                                 status                      VARCHAR(50)     NOT NULL,
                                 mpesa_receipt_number        VARCHAR(255),
                                 version                     BIGINT          NOT NULL DEFAULT 0,
                                 created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                 updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                                 CONSTRAINT pk_payment_intents PRIMARY KEY (id)
);

CREATE INDEX idx_payment_intents_unit_id                   ON payment_intents (unit_id);
CREATE INDEX idx_payment_intents_property_id               ON payment_intents (property_id);
CREATE INDEX idx_payment_intents_mpesa_checkout_request_id ON payment_intents (mpesa_checkout_request_id);
CREATE INDEX idx_payment_intents_status                    ON payment_intents (status);

-- =====================================================
-- RESERVATIONS
-- =====================================================
CREATE TABLE reservations (
                              id                      UUID            NOT NULL,
                              unit_id                 UUID            NOT NULL,
                              property_id             UUID            NOT NULL,
                              full_name               VARCHAR(255)    NOT NULL,
                              phone                   VARCHAR(20)     NOT NULL,
                              email                   VARCHAR(255)    NOT NULL,
                              national_id             VARCHAR(50)     NOT NULL,
                              mpesa_phone             VARCHAR(20)     NOT NULL,
                              move_in_date            DATE            NOT NULL,
                              deposit_amount          NUMERIC(19, 2)  NOT NULL,
                              status                  VARCHAR(50)     NOT NULL,
                              mpesa_receipt_number    VARCHAR(255),
                              clerk_user_id           VARCHAR(255),
                              payment_intent_id       UUID            NOT NULL,
                              version                 BIGINT          NOT NULL DEFAULT 0,
                              created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                              updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                              CONSTRAINT pk_reservations              PRIMARY KEY (id),
                              CONSTRAINT fk_reservations_payment_intent
                                  FOREIGN KEY (payment_intent_id) REFERENCES payment_intents (id)
);

CREATE INDEX idx_reservations_unit_id           ON reservations (unit_id);
CREATE INDEX idx_reservations_property_id       ON reservations (property_id);
CREATE INDEX idx_reservations_status            ON reservations (status);
CREATE INDEX idx_reservations_payment_intent_id ON reservations (payment_intent_id);