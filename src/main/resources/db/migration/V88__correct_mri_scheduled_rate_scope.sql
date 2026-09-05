-- Corrects a mis-scoped MRI rate row seeded by V58.
--
-- V58 staged a SCHEDULED 10% row effective 2026-07-01, sourced to
-- "Finance Act 2026 (UNVERIFIED — advisor sign-off required)", on the
-- understanding that the Act might revert the general residential rate from
-- 7.5% back to 10%. Its rate and its date were right. Its SCOPE was wrong.
--
-- What the Finance Act 2026 actually did (verified 2026-09-04 against PwC's
-- Kenya tax summary and Kenyan practitioner commentary):
--
--   * The RESIDENT residential rate is UNCHANGED at 7.5%. The Finance Act
--     2023 set it effective 2024-01-01 and the 2026 Act did not move it. The
--     "rate rises to 10%" reporting described a Finance BILL proposal that
--     did not become the resident rate.
--
--   * The Act introduced section 6B: a NEW, SEPARATE 10% final tax on the
--     gross rental income of NON-RESIDENT landlords, effective 2026-07-01 —
--     the same date this row carries. Non-residents register through a
--     simplified framework and file by the 20th of the following month. No
--     deductions. The obligation sits on the landlord, not as a withholding
--     duty on the tenant.
--
-- The danger this migration removes: the row sat in a table with no residency
-- dimension, one step (MriRatePolicyService.activateRate) away from becoming
-- THE active rate for everyone. Activating it would have charged every
-- resident landlord 10% instead of 7.5% — over-taxing them by a third, on a
-- rate the law does not impose on them, in a filing they are personally
-- liable for.
--
-- It is deleted rather than reclassified because mri_rate_schedule cannot
-- express "non-resident only": it has no residency column, and
-- findActiveAsOf(date) selects purely on date. Leaving the row with a comment
-- would preserve exactly the hazard described above. Non-resident support
-- needs a residency dimension on both the schedule and the filing, plus a
-- residency field on the landlord that the system does not collect today —
-- see TD-121. Until that exists, the honest state is that this system
-- computes the resident regime only.
--
-- The ACTIVE 7.5% row (Finance Act 2023) is untouched and remains correct.

DELETE FROM mri_rate_schedule
WHERE id = '01000000-0000-4000-8000-000000000002'
  AND status = 'SCHEDULED';

-- Guard: if the row was already activated on some environment, this
-- migration must not silently leave a wrong ACTIVE rate behind. Fail loudly
-- instead — an over-taxed filing is not something to paper over.
DO $$
DECLARE
    wrong_active INTEGER;
BEGIN
    SELECT COUNT(*) INTO wrong_active
    FROM mri_rate_schedule
    WHERE id = '01000000-0000-4000-8000-000000000002'
      AND status = 'ACTIVE';

    IF wrong_active > 0 THEN
        RAISE EXCEPTION
            'The Finance Act 2026 10%% row was activated as a general MRI rate. '
            'That rate applies to NON-RESIDENT landlords only (ITA s.6B); the '
            'resident rate is 7.5%%. Filings computed since activation are '
            'over-stated and must be reviewed with a tax advisor before this '
            'migration can proceed.';
    END IF;
END $$;
