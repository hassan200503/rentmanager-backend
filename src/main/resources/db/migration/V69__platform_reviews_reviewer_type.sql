-- V69: Platform reviews — snapshot the reviewer's side.
--
-- Purpose: the public testimonials wall must label every quote with the
-- reviewer's role (landlord or renter). Landlords and renters are
-- different people with different opinions of the platform, so their
-- reviews must be distinguishable from each other after approval.
-- Existing rows are backfilled below; every new submission snapshots
-- the role at write time from the users table.

ALTER TABLE platform_reviews
    ADD COLUMN reviewer_type VARCHAR(30) NOT NULL DEFAULT 'LANDLORD';

-- Backfill the seeded sample testimonials with their intended roles.
UPDATE platform_reviews
SET reviewer_type = CASE reviewer_user_id
    WHEN 'ffffffff-0000-0000-0000-000000000001' THEN 'RENTER'
    WHEN 'ffffffff-0000-0000-0000-000000000002' THEN 'LANDLORD'
    WHEN 'ffffffff-0000-0000-0000-000000000003' THEN 'RENTER'
    WHEN 'ffffffff-0000-0000-0000-000000000004' THEN 'RENTER'
    WHEN 'ffffffff-0000-0000-0000-000000000005' THEN 'RENTER'
END
WHERE reviewer_user_id::text LIKE 'ffffffff-%';

-- Backfill real reviews: a user with a tenant link is a landlord; a user
-- with a tenant_profile row (and no tenant link) is a renter. Anything
-- unresolvable keeps the LANDLORD default.
UPDATE platform_reviews pr
SET reviewer_type = 'RENTER'
WHERE pr.reviewer_user_id::text NOT LIKE 'ffffffff-%'
  AND NOT EXISTS (
      SELECT 1 FROM users u2
      WHERE u2.id = pr.reviewer_user_id AND u2.tenant_id IS NOT NULL
  )
  AND EXISTS (
      SELECT 1
      FROM tenant_profile tp
      JOIN users u ON u.clerk_user_id = tp.clerk_user_id
      WHERE u.id = pr.reviewer_user_id
  );

ALTER TABLE platform_reviews
    ADD CONSTRAINT ck_platform_reviews_reviewer_type
        CHECK (reviewer_type IN ('LANDLORD', 'RENTER'));