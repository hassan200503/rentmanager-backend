-- V70__enforce_single_active_commission_policy.sql
--
-- WHY THIS EXISTS
--
-- CommissionPolicyJpaRepository declares:
--     Optional<CommissionPolicyJpaEntity> findByLandlordOrgIdAndActiveTrue(UUID)
--     Optional<CommissionPolicyJpaEntity> findByLandlordOrgIdIsNullAndActiveTrue()
--
-- Both promise at most one active row. V44 never enforced that promise:
-- idx_commission_policies_landlord is a plain (non-unique) partial index.
-- CommissionPolicyService.setLandlordRate()/setDefaultRate() deactivate the
-- previous row and insert a new one inside one transaction, but two
-- concurrent admin saves — or any partial write — can leave two active rows
-- for the same landlord.
--
-- The failure is not cosmetic. Once a second active row exists, Spring Data
-- throws IncorrectResultSizeDataAccessException from getActiveRate(), and it
-- throws inside RentPaymentCallbackTransactionService.handleSuccessfulCallback
-- AFTER rentLedgerApplicationService.applyTransaction() has credited the
-- ledger but BEFORE commission is computed and the B2C disbursement is
-- created. Net effect: the tenant's rent is recorded as paid and the
-- landlord silently stops being disbursed, for every payment, until someone
-- notices.
--
-- WHAT THIS DOES
--
-- 1. Deactivates duplicate active rows, keeping the one the service layer
--    would itself consider newest (latest effective_from, then created_at,
--    then id as a stable tiebreak). Deterministic and re-runnable.
-- 2. Adds the unique index the repository signatures already assume.
--    COALESCE maps the platform default (landlord_org_id IS NULL) onto a
--    sentinel, because a plain UNIQUE index does NOT constrain multiple
--    NULLs in PostgreSQL — without it, two active platform defaults would
--    still slip through.
-- 3. Adds the range CHECK on rate_percent. A commission rate outside 0..100
--    is not a business case; it is a bug or a bad admin input, and it moves
--    money. If this migration fails here, real data violates it — fix the
--    data, do not weaken the constraint.
--
-- SAFETY: additive. No column is dropped, no row is deleted, and any row
-- deactivated in step 1 remains readable as history.

-- ---------------------------------------------------------------------
-- 1. Collapse duplicate active policies
-- ---------------------------------------------------------------------
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY COALESCE(landlord_org_id, '00000000-0000-0000-0000-000000000000'::uuid)
               ORDER BY effective_from DESC, created_at DESC, id DESC
           ) AS rn
    FROM commission_policies
    WHERE active = TRUE
)
UPDATE commission_policies c
SET active = FALSE,
    updated_at = now()
FROM ranked r
WHERE c.id = r.id
  AND r.rn > 1;

-- ---------------------------------------------------------------------
-- 2. Enforce at most one active policy per landlord, and one active
--    platform default.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uk_commission_policies_one_active
    ON commission_policies (
        COALESCE(landlord_org_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE active = TRUE;

-- ---------------------------------------------------------------------
-- 3. A commission rate is a percentage.
-- ---------------------------------------------------------------------
ALTER TABLE commission_policies
    ADD CONSTRAINT ck_commission_policies_rate_range
        CHECK (rate_percent >= 0 AND rate_percent <= 100);
