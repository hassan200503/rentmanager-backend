# A1 Legal Gates — KRA Tax Compliance Evidence

Status: reviewed against web sources on 2026-08-03 (Finance Act 2026 already assented and in force).
Purpose: record, for each fail-closed legal gate in the tax module, what we built, why it is lawful, and the evidence a tax advisor can check. All gates ship **disabled / dormant by default** — nothing computes, transmits, or files until the advisor signs off and the corresponding config toggle is flipped.

---

## Gate 1 — 16% VAT on commercial rent

- **Verdict:** lawful to build, **disabled by default** (`app.tax.vat-branch-enabled=false`). Commercial rent is standard-rated 16% VAT under the VAT Act 2013 First Schedule Part II para 8; residential rent is exempt.
- **Design:** `VatTreatment.forPremises()` returns `STANDARD_RATED` only for COMMERCIAL premises **and** a VAT-registered landlord (`Tenant.vatRegistered == true`); otherwise `VAT_EXEMPT` (fail-closed). `RentPaymentAppliedTaxInvoiceListener` gates the whole branch on `vatBranchEnabled`.
- **Open risk:** High Court in *Ndegwa v KRA* read the para 8 exemption as covering commercial premises too; KRA appealed (**KECA 2025/510**, 2025-03-21, appeal outcome pending). The disable-by-default posture keeps us safe under both readings.
- **Advisor note:** VAT registration is compulsory only where turnover exceeds the KES 5,000,000 threshold; the landlord `vat_registered` flag is a manual CRM fact, defaults FALSE — no KRA lookup exists to verify it automatically.

Sources:
- https://kra.go.ke — VAT Act 2013, First Schedule Part II para 8
- https://new.kenyalaw.org — KECA 2025/510 (KRA v Ndegwa, appeal of 2025-03-21)
- https://www.andersen.co.ke — commercial rent subject to 16% VAT above the KES 5M threshold
- https://pangoni.io/blog/compliance/kra-etims-landlords-kenya — eTIMS mandate for landlords incl. commercial rent (Jan 2026 expansion)

---

## Gate 2 — landlord `vat_registered` flag

- **Verdict:** lawful as built. The system never assumes a landlord is VAT-registered; `Tenant.vatRegistered` defaults FALSE and is set only on explicit confirmation (`Tenant.updateKraTaxProfile()`).
- **Design:** this flag is the only way a commercial-rent invoice can ever become STANDARD_RATED, so it is the second fail-closed condition behind `vatBranchEnabled`.
- **Advisor note:** confirm the desired default for landlords the business knows to be registered above the 5M threshold, and whether the flag should be surfaced on landlord onboarding forms.

Sources:
- https://kra.go.ke/individual/filing-paying/types-of-taxes/vat — VAT registration threshold KES 5,000,000 (mainland Kenya)
- https://www.andersen.co.ke — rent income counts toward the VAT threshold

---

## Gate 3 — MRI rate: Finance Act 2026 10% proposal

- **Verdict:** **RESOLVED — rate remains 7.5%.** The 10% was a Finance Bill 2026 *proposal* and was dropped before enactment. Finance Act 2026 (No. 19 of 2026, assented 23 June 2026, in force 1 July 2026) made no MRI change.
- **Design:** `mri_rate_schedule` (V58) seeds 7.5% ACTIVE + 10% SCHEDULED. `MriRatePolicyService` uses ACTIVE rows only; SCHEDULED is never computed (fail-closed). Correct and safe: if a future Finance Act restores 10%, we activate a row — no code change.
- **Advisor note:** keep the 10% row dormant; 7.5% (Finance Act 2023, effective 1 Jan 2024) is current law. Note that Finance Act 2026 instead introduced a new **non-resident rental income tax** (NRRIT: 30% of gross rent on immovable property, 15% other; monthly filing by the 20th; resident-agent exemption) — out of scope for this module but relevant to diaspora landlords.

Sources:
- https://www.kra.go.ke/individual/filing-paying/types-of-taxes/residential-rental-income — 7.5% effective 1 Jan 2024, final tax on gross rent, band KES 280,001–15,000,000/yr
- https://www.youtube.com/watch?v=wDcd3DOsOGo — Citizen TV (2026-05-11): CS Mbadi confirms 7.5% retained, 10% dropped
- https://www.instagram.com/reel/DYwn_xCggj1/ — Money254: Treasury dropped the rental-income proposal among 5 withdrawn items
- https://assets.kpmg.com/.../Finance-Act-2026_KPMG-Analysis.pdf — enacted changes (NRRIT 30%/15% etc.); no MRI change
- https://aln.africa/insight/analysis-of-the-tax-changes-introduced-by-the-finance-act-2026/ — assent 23 Jun 2026, gazetted 26 Jun 2026, effective 1 Jul 2026
- https://www.rsm.global/kenya/.../RSMEA%20Newsletter...Finance%20Bill%202026.pdf — the *proposal* (7.5% → 10%) that was later dropped
- https://fnjassociates.co.ke/understanding-monthly-rental-income-tax-in-kenya/ — rate history 10% (2016–2023) → 7.5% (2024–), NRRIT from 1 Jul 2026

---

## Gate 4 — eRITS tenant KRA PIN requirement

- **Verdict:** confirmed real. eRITS property/unit registration requires the **tenant's name and KRA PIN**; where a tenant cannot supply a PIN there is a no-PIN flag, but the unit is tagged for follow-up. Property registration on eRITS is independent of return filing (can be done at any time; must not precede the filing of the return).
- **Design:** `tenants.kra_pin` and `tenant_profile.kra_pin` are optional columns (V56/V57) — matches the real flow (PIN expected; no-PIN fallback exists). `MonthlyRentalIncomeFiling` computes from aggregated residential rent payments regardless of PIN presence, so a missing PIN never blocks tax computation.
- **Advisor note:** decide whether a "no PIN captured" flag should be tracked per unit for the eRITS follow-up-tag workflow, and whether a landlord-facing prompt should be added at tenant onboarding.

Sources:
- https://www.kra.go.ke/images/publications/Step-by-step-guide-for-MRI-Registration-Filing-and-Payment.pdf — "PIN of a tenant of the said property" at property registration; registration independent of filing
- https://pangoni.io/blog/compliance/kra-erits-landlords-kenya — eRITS data model (property, unit, lease, tenant), 7.5% auto-calc, filing by the 20th
- https://www.real.co.ke/erits-landlord-registration-kenya/ — "The system requires the tenant's name and KRA PIN. Where a tenant cannot supply a PIN, there is a flag for that, but expect the unit to be tagged for follow-up"
- https://www.kra.go.ke/news-center/public-notices/2306-on-boarding-of-rental-properties-on-erits-2 — official eRITS rollout notice

---

## Competitor practice (Kenyan PMS / tax stack)

- **Pangoni (pangoni.io)** — closest analogue: eTIMS invoice transmission in real time via landlord PIN (no TIMS device), one-click eRITS-ready monthly MRI summary, 5-year receipt retention, multi-landlord support. eRITS direct submission "being readied".
- **Real Management Services (real.co.ke)** — full-service letting agent: registers portfolios on eRITS, chases tenant PINs, files monthly returns on the landlord's behalf (manual/operational, not API).
- Both treat eTIMS (invoice layer) + eRITS (reporting layer) as the two halves of the stack; our Phase 1 (invoice generation + transmission stubs + monthly filing computation) matches this shape.

## Where the toggles live

- `com.rentmanager.modules.tax.application.config.TaxProperties` — `invoiceEnabled`, `filingEnabled`, `vatBranchEnabled`, `maxAttempts`, `transmissionBatchSize`
- `src/main/resources/db/migration/V58__create_mri_rate_schedule.sql` — 7.5% ACTIVE + 10% SCHEDULED seeds
- `com.rentmanager.modules.tax.domain.enums.VatTreatment` — fail-closed VAT classification
- `com.rentmanager.modules.tax.application.service.MriRatePolicyService` — ACTIVE-only rate lookup
- `com.rentmanager.modules.tax.infrastructure.transmission/*` — NOT_AVAILABLE stubs (Phase 1 never transmits)
