-- V87 PRE-FLIGHT — run this BEFORE deploying, against production.
--
-- V87 decides which existing landlords keep a "verified" badge. The rule is
-- "has at least one lease that reached a real state → ACTIVE, otherwise
-- PENDING". This shows you exactly who lands where, so the decision is yours
-- rather than mine.
--
-- Nothing here writes. Safe to run any time.

-- 1. What the backfill will do, in one row.
SELECT
    COUNT(*) FILTER (WHERE status IS NOT NULL)                    AS already_set_untouched,
    COUNT(*) FILTER (WHERE status IS NULL AND has_lease)          AS will_become_active,
    COUNT(*) FILTER (WHERE status IS NULL AND NOT has_lease)      AS will_become_pending
FROM (
    SELECT t.id, t.status,
           EXISTS (SELECT 1 FROM leases l
                   WHERE l.tenant_id = t.id
                     AND l.status IN ('ACTIVE','RENEWED','EXPIRED','TERMINATED')) AS has_lease
    FROM tenants t
) x;

-- 2. Who loses the badge. Review this list — these are the landlords who will
--    show as unverified until an admin approves them via
--    PATCH /api/v1/admin/landlords/{id}/status.
--
--    A landlord here who is legitimately set up but has not signed a lease
--    yet is the expected false negative. Approving them is one call.
SELECT t.id,
       t.name,
       t.subscription_status,
       t.created_at,
       (SELECT COUNT(*) FROM leases l WHERE l.tenant_id = t.id) AS lease_count,
       (SELECT COUNT(*) FROM properties p WHERE p.tenant_id = t.id) AS property_count
FROM tenants t
WHERE t.status IS NULL
  AND NOT EXISTS (SELECT 1 FROM leases l
                  WHERE l.tenant_id = t.id
                    AND l.status IN ('ACTIVE','RENEWED','EXPIRED','TERMINATED'))
ORDER BY property_count DESC, t.created_at;

-- 3. The specific case worth a second look: a landlord with properties and
--    units set up, collecting nothing yet. They have clearly onboarded, and
--    the lease test will still mark them PENDING.
SELECT t.id, t.name,
       (SELECT COUNT(*) FROM units u WHERE u.tenant_id = t.id) AS unit_count
FROM tenants t
WHERE t.status IS NULL
  AND NOT EXISTS (SELECT 1 FROM leases l
                  WHERE l.tenant_id = t.id
                    AND l.status IN ('ACTIVE','RENEWED','EXPIRED','TERMINATED'))
  AND EXISTS (SELECT 1 FROM units u WHERE u.tenant_id = t.id)
ORDER BY unit_count DESC;

-- If (3) returns landlords you would rather grandfather, run this BEFORE
-- deploying and V87 will leave them alone (it only touches status IS NULL):
--
--   UPDATE tenants SET status = 'ACTIVE'
--   WHERE id IN ( ...ids you approve... );
