-- V101: Self-service account deletion requests.
--
-- Both app stores require that a person who can create an account in the app
-- can also start deleting it from the app. This table is the record of every
-- request and what was done, so a support or data-protection query can be
-- answered from facts rather than logs.
--
-- It stores the Clerk user id of the person who asked (needed to answer "did
-- this person ask, and what happened"), no name, email or phone.
--
-- status:
--   COMPLETED       login removed and local identity erased
--   PENDING_REVIEW  cannot be done automatically (the person owns a landlord
--                   organisation that other people and records depend on)
--   FAILED          the identity provider refused; nothing was erased locally

CREATE TABLE account_deletion_requests (
    id              UUID         NOT NULL,
    clerk_user_id   VARCHAR(255) NOT NULL,
    source          VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    detail          VARCHAR(500),
    requested_at    TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT pk_account_deletion_requests PRIMARY KEY (id),
    CONSTRAINT ck_account_deletion_status CHECK (status IN ('COMPLETED', 'PENDING_REVIEW', 'FAILED')),
    CONSTRAINT ck_account_deletion_source CHECK (source IN ('APP', 'CLERK_WEBHOOK'))
);

CREATE INDEX idx_account_deletion_requests_clerk_user ON account_deletion_requests (clerk_user_id, requested_at DESC);
