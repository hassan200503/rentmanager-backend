-- V66: Platform reviews — users rating the platform itself.
--
-- Purpose: the public testimonials feed must express *trust in the
-- platform*, not landlord-service quality. That is the job of this new,
-- third, platform-wide review kind (landlord or renter -> RentManager).
-- It deliberately has NO tenant_id column: a platform review is not
-- scoped to any landlord account, and the tenant-isolation machinery of
-- the other two review tables does not apply.
--
-- Rules (mirrored from LandlordReview/RenterReview):
--   * exactly one review per user (uq on reviewer_user_id) — repeat
--     submissions UPDATE the same row (in-place editing, re-moderation);
--   * new rows enter PENDING; only APPROVED rows reach
--     /api/v1/public/testimonials;
--   * the reviewer's name is snapshotted at write time from the users
--     table (public feeds only ever show the first name).

CREATE TABLE platform_reviews (
    id               UUID         NOT NULL,
    reviewer_user_id UUID         NOT NULL,
    reviewer_name    VARCHAR(100) NOT NULL,
    rating           SMALLINT     NOT NULL,
    comment          VARCHAR(1000),
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_platform_reviews PRIMARY KEY (id),
    CONSTRAINT ck_platform_reviews_rating
        CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_platform_reviews_status
        CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN')),
    CONSTRAINT uq_platform_reviews_reviewer UNIQUE (reviewer_user_id)
);

CREATE INDEX idx_platform_reviews_status_created
    ON platform_reviews (status, created_at);

CREATE INDEX idx_platform_reviews_reviewer
    ON platform_reviews (reviewer_user_id);

-- Seed sample testimonials (APPROVED) so the public wall renders the
-- very first time the environment boots. reviewer_user_ids live in the
-- ffffffff- service namespace and can never collide with real Clerk
-- users. Remove this block on environments that want a cold wall.
INSERT INTO platform_reviews
    (id, reviewer_user_id, reviewer_name, rating, comment, status, version, created_at, updated_at)
VALUES
    ('ffffffff-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001', 'Amina Hassan',  5,
     'Verified my landlord in minutes and every rent payment lands instantly with a digital receipt. The portal saved our whole family from endless phone calls.',
     'APPROVED', 0, now() - interval '34 days', now() - interval '34 days'),
    ('ffffffff-0000-0000-0000-000000000002', 'ffffffff-0000-0000-0000-000000000002', 'Brian Otieno',
     5, 'Onboarding renters with e-signatures is effortless. Collections that used to take me a week now settle themselves.',
     'APPROVED', 0, now() - interval '23 days', now() - interval '23 days'),
    ('ffffffff-0000-0000-0000-000000000003', 'ffffffff-0000-0000-0000-000000000003', 'Grace Wanjiku',
     4, 'The tenant portal keeps everything organised — reminders, receipts, maintenance. Payments that were once overdue are rare now.',
     'APPROVED', 0, now() - interval '17 days', now() - interval '17 days'),
    ('ffffffff-0000-0000-0000-000000000004', 'ffffffff-0000-0000-0000-000000000004', 'Daniel Kipchoge',
     5, 'I rated my pass our rental an easy 5 stars — the platform is the middleman that finally works. M-Pesa receipts are immediate.',
     'APPROVED', 0, now() - interval '9 days', now() - interval '9 days'),
    ('ffffffff-0000-0000-0000-000000000005', 'ffffffff-0000-0000-0000-000000000005', 'Lilian Achieng',
     4, 'Reserving a unit, signing the lease and paying deposit happened in a single afternoon. Transparent from the first click.',
     'APPROVED', 0, now() - interval '4 days', now() - interval '4 days');