# RentManager — backend

> **The original ten-defect list is closed.** Every item was fixed and
> verified against the source on 2026-09-04; the evidence is in the table
> below, kept so nobody re-"fixes" code that is already correct. The list was
> originally compiled from the commit of 2026-08-01 (`cefc59b`) plus the
> Flyway migrations and the frontend working tree.
>
> **What is actually open now lives in
> `../rentmanager-frontend/docs/ai/TECHNICAL_DEBT.md`**, with the reasoning
> in `DECISION_LOG.md` next to it. Read those before starting work; this file
> is architecture and rules, not a to-do list.

Kenya-first rental management SaaS. Java 21, Spring Boot 3.2.5, PostgreSQL,
Flyway, Clerk (JWT via JWKS), M-Pesa Daraja. Modular monolith:
`com.rentmanager.modules.<module>.{api,application,domain,infrastructure}`
with ports/adapters. Keep that shape; do not flatten it.

## Commands

```
mvn test -f "C:\JavaProjects\rentmanager-backend" -pl .                    # full suite
mvn test -f "C:\JavaProjects\rentmanager-backend" -Dtest="ClassName" -pl . # one class
docker compose up -d postgres                                             # local DB
```

Integration tests use Testcontainers and need Docker running.

## The naming trap — read this before touching any query

`tenants` is the **landlord's SaaS organisation**, not a renter. It carries
`clerk_org_id`, branding, `commission_rate`, `billing_mode`, encrypted Daraja
credentials and `payout_phone_number`. The **renter** is `tenant_profile`
(singular table name), keyed `(tenant_id, clerk_user_id)` because one person
may rent from several landlords.

So `tenant_id` on every table means *which landlord owns this row* — it is the
tenancy discriminator, never "the renter". `tenant_profile_id` is the renter.
Getting this backwards silently crosses organisations. If you rename anything,
rename it everywhere in one change, migration included.

## Security model — verified, do not "fix" it

- Tenant identity comes from `ClerkJwtAuthenticationConverter`: the verified
  JWT's `tenant_id` claim (a Clerk org id) → `tenants.clerk_org_id` lookup →
  `TenantContext.setTenantId(...)`. **`X-Tenant-Id` is never read.** The
  frontend sends it; the backend ignores it. Do not start trusting it, and do
  not put a tenant/org id in a URL path as the authority.
- `TenantContext` is a ThreadLocal cleared by `TenantContextCleanupFilter` at
  `Ordered.HIGHEST_PRECEDENCE` so its `finally` runs after Spring Security's
  chain unwinds. This ordering is load-bearing on a pooled executor. Leave it.
- Tenants are deliberately **not** auto-provisioned. An authenticated user with
  no matching local tenant gets `tenantId == null` and `ROLE_PENDING_ONBOARDING`.
- Fine-grained authorities: `ROLE_LANDLORD_OWNER | _MANAGER | _STAFF`, from
  `users.role`. Money-moving actions (adjustments, refunds, overpayment
  resolution, Daraja credentials) are OWNER/MANAGER. Recording a payment is
  also STAFF on purpose — a caretaker collects the cash.
- Every controller method that touches tenant data must carry `@PreAuthorize`
  **and** scope its repository call by `TenantContext.getTenantId()`. Both.

## The original defect list — all ten closed, with evidence

Verified 2026-09-04 by reading the source, not the docs. Do not re-fix these.
If one looks broken, something regressed — check the migration or class named
here before assuming the entry is stale.

| # | Defect | Closed by |
|---|--------|-----------|
| 1 | `dev` profile default; `/api/v1/dev/**` was `permitAll` | `application.yml` now reads `active: ${SPRING_PROFILES_ACTIVE:prod}`; the dev matcher was removed from `SecurityConfig` |
| 2 | `DELETE /rent-ledger/transactions/{id}` hard-deleted financial history | The endpoint now posts a compensating REVERSAL and keeps both rows (`reverseTransaction`). `V81` also blocks a real DELETE at the database |
| 3 | No foreign keys on the core tables | `V72`–`V78`, one table per migration: properties, units, leases, rent_ledger_entries, deposits, disbursements, unmatched_payments |
| 4 | No currency column on any money table | `V79__add_currency_to_money_tables.sql` |
| 5 | Dead `tenants.commission_rate` alongside `commission_policies.rate_percent` | `V80__drop_dead_tenants_commission_rate.sql` |
| 6 | `validateTenantAccess` compared `getOrganizationId()` | `TenantCommandServiceImpl` now compares `tenant.getId()`, with the reasoning in a comment above it |
| 7 | `rent_transactions` mutable; no money CHECK constraints; `amount_paid` unreconciled | `V81` append-only trigger (`BEFORE UPDATE OR DELETE`, commission snapshot excepted), `V82` money CHECKs, `RentLedgerReconciliationScheduler` |
| 8 | The `deposit` module had no controller | `DepositController` exists and is authorised |
| 9 | `ClerkJwtAuthenticationConverter` ran 2–3 queries per request | Identity cache keyed by clerk user id plus org id, short TTL — see `CACHE_TTL_MILLIS` |
| 10 | Money serialised as a JSON number | `JacksonConfig` serialises every `BigDecimal` as a string globally. **Renter response types are `MoneyValue` on the client for this reason** |

**Also fixed, do not redo:** `V70__enforce_single_active_commission_policy.sql`
adds the unique partial index the `Optional` repository signatures always
assumed. Before it, two active policies for one landlord made
`getActiveRate()` throw *inside the M-Pesa callback*, after the ledger was
credited and before the disbursement was created — rent recorded, landlord
silently unpaid.

**Two money paths were hardened later and are easy to undo by accident:**

- **Disbursement (ADR-0018).** Entitlement is reserved under a
  `PESSIMISTIC_WRITE` lock on the ledger entry, taken *before*
  `settleableAmount` is read, in `DisbursementTransactionService` — a separate
  bean, because a self-invoked `@Transactional` call would bypass the proxy
  and open neither transaction nor lock. The Daraja call must stay outside
  that transaction. Reverting the locked finder to the plain one is a silent
  double-payout; `DisbursementTransactionServiceTest` asserts the ordering.
- **Rent STK push (ADR-0016).** `doInitiate` returns any PENDING request for
  the entry created within a 3-minute window instead of sending a second
  push.

**Still unexamined, and honestly so:** the `tax`, `platformsettings`,
`integration` and platform-review modules (migrations V56–V69) have never been
read in an audit. Treat them as unexamined rather than clean. The landlord
dashboard UI is likewise unaudited — the renter portal has had a full pass and
the landlord side has not.

## Commercial constraint that governs the payment code

Collecting rent into a platform M-Pesa account, deducting commission and
disbursing the net is payment **aggregation**. Safaricom's M-PESA terms
cl. 15.2(l) prohibit it without their written consent, and it requires CBK
authorisation under the NPS Act 2011 (Electronic Retail PSP: KSh 5m core
capital, 4–9 months). B2C also requires pre-funding a trust account (cl. 6.1(a)).

The compliant path already exists: per-landlord Daraja credentials (`V28`,
AES-256-GCM at rest), where rent lands in the landlord's own Paybill and the
platform never takes custody. Treat that as the default. Keep the commission
and B2C code, but behind a flag marked *requires CBK authorisation* — do not
extend it, and do not make it the default `billing_mode`, until a licence path
is decided.

## Rules

- Never weaken or delete a failing test to make a build green. Fix the cause.
- Never modify an applied migration. Add a new one.
- Test conventions: no `@Mock`/`@InjectMocks`/`@ExtendWith(MockitoExtension)` —
  strict stubbing breaks on shared `@BeforeEach` setup. Use manual
  `mock(Class.class)`; create all domain mocks first, then stub. Never call a
  `mockXxx()` helper inside `when(...)`.
- Do not add Redis, Kafka or a broker. The outbox + `@Scheduled` sweep pattern
  is deliberate and sufficient at this scale.
- Do not log secrets, Daraja credentials, full phone numbers (`PhoneMasker`
  exists) or KRA PINs.
- Anything that moves money is `BigDecimal`, idempotent, and tenant-scoped at
  the repository call, not just at the controller.
