# Going live: switching Clerk (and everything else) to production

Everything in this repository is production-shaped already. One thing is not,
and it is the only thing standing between this deployment and real users at
scale: **Clerk is running a development instance.**

## Why this cannot be done in code

A Clerk production instance requires a domain you own and can add DNS records
to. Clerk's own documentation is unambiguous: *"You will need to have a domain
you own"* and *"be able to add DNS records on your domain."* A hosting
provider's subdomain — `rentmanager-ke.netlify.app` — will not work, because
nobody but Netlify can add records under `netlify.app`.

So this is a registration decision, not an engineering task.

**And it is probably not urgent.** It is easy — this document did it — to
present the development instance as the thing standing between here and real
users. Check the numbers before believing that. The platform currently has 2
landlord organisations, 0 renters and 0 active leases. The 100-user cap is not
close to binding, and will not be for a long time. What the development
instance actually costs today is one thing: a **"Development mode" badge on the
sign-in form**, which every prospective landlord sees.

So the honest order of work is: get the first real landlord and renter through
the reserve → pay → lease → rent flow, which has never completed once. Sort the
domain when it is affordable or when the badge starts costing sign-ups.

## What the development instance costs you today

| | Development instance |
|---|---|
| Users | **Hard cap of 100.** Not a soft limit. |
| Sign-in form | Shows a **"Development mode"** badge to every visitor |
| Google sign-in | Uses Clerk's **shared** OAuth credentials |
| Data | **Separate from production.** Users and organisations do **not** transfer |

That last row is the one people get caught by. Switching to production does not
migrate anyone: the production instance starts empty. Do it *before* you invite
real users, not after, or you will be asking your first landlords to sign up
twice.

## The domain

### If there is no money at all

`eu.org` is the only free route that survives scrutiny. Apply at
[nic.eu.org](https://nic.eu.org/) and **say plainly what the project is**. The
registry's stated purpose is free registration "to users or non-profit
organizations who cannot afford the fees demanded by some NICs", which is a
description of this situation — but it is their call, not ours, so the
application should be honest rather than carefully worded. Approval is manual
and can take weeks. It costs nothing but the wait, so it is worth starting
early even if the switch happens much later.

One technical note: `eu.org` only lets you set **NS** records, so you point it
at nameservers you control — Cloudflare's DNS is free and does this — and add
Clerk's CNAMEs there.

### When there is money, this is cheap

`.co.ke` costs **KSh 999–1,200 a year** and Kenyan registrars — Truehost,
Sasahost, Kenya Website Experts, Buy Domain Kenya — take **M-Pesa**, so no card
is needed. About KSh 85 a month. For a Kenyan rental platform it is also the
right answer on its own merits: renters are being asked to send a deposit to
someone they have not met, and the domain is part of why they believe the site
is real.

**Why the other free route is not an option:**

- **`is-a.dev` is not available to this project.** Its Terms of Service state a
  subdomain "may not be ... monetised, whether directly or indirectly" and that
  the holder "must be an individual person, not an organisation". RentManager
  sells landlord subscriptions. Using it would breach those terms, and a
  revoked subdomain does not just break a link — Clerk's Frontend API would be
  served from it, so **sign-in would stop working for everyone at once**.


## The switch, once you have a domain

Nothing here needs a code change. Every item is configuration.

1. **Clerk Dashboard → create the production instance**, then Domains, and add
   the CNAME records it lists to your registrar. Propagation can take up to
   48 hours, so do this first.
2. **Your own Google OAuth credentials.** Production cannot use Clerk's shared
   ones. Create them in Google Cloud Console; the consent screen needs a privacy
   policy URL, which is why `/legal/privacy` exists and is public.
3. **Swap the keys.** `pk_live_…` and `sk_live_…` replace the `pk_test_`/
   `sk_test_` pair:
   - Netlify → `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`
   - Render → `CLERK_SECRET_KEY`, `CLERK_JWKS_URL` (the production JWKS URL)
4. **Re-create the JWT template** named `backend`, with the same claims:
   `tenant_id={{org.id}}`, `email`, `platformRole`, `userType`. Templates do not
   transfer between instances. **If you skip this, every authenticated request
   fails**, because the backend reads `tenant_id` from this template.
5. **Re-point the webhooks** and copy the new signing secrets:
   - Backend: `https://<api>/api/v1/webhooks/clerk` → `CLERK_WEBHOOK_SECRET`
   - Web: `https://<site>/api/webhooks/clerk` → `CLERK_WEBHOOK_SIGNING_SECRET`
6. **Re-apply the two instance settings** this deployment depends on, because
   they are per-instance and will be back at their defaults:
   - Organizations → Settings → **Membership optional**. Renters have no
     organisation; with "required" they hit a dead end at sign-in.
   - Configure → Legal → express consent on, with the terms and privacy URLs.
7. **Point `CORS_ALLOWED_ORIGINS`** (Render) and `NEXT_PUBLIC_APP_URL`
   (Netlify) at the new domain.

## What you do *not* have to touch

- **The Content-Security-Policy.** `next.config.ts` derives the Clerk Frontend
  API origin from the publishable key itself (the key is
  `pk_(test|live)_` + base64 of the host), so a `pk_live_` key moves the CSP to
  `clerk.<your-domain>` automatically. This was verified by decoding a
  synthetic production key. A CSP pinned to the development domain would have
  blocked Clerk entirely at launch — sign-in failing silently on day one — so
  it is worth knowing it is already handled.
- Migrations, the database, the API, the backup workflow, the cron keep-alive.

## Verify after switching

Run these; they are the same checks used throughout the build.

```bash
API=https://<your-api>/api/v1
curl -s -o /dev/null -w "%{http_code}\n" "$API/users/me/access"                 # 401
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer eyJhbGciOiJub25lIn0.e30." "$API/renters"   # 401
curl -sI "$API/public/platform/branding" | grep -i cache-control                # max-age=300, public
curl -s -o /dev/null -w "%{http_code}\n" "https://<your-api>/actuator/health"   # 404
```

Then in a browser, signed out: the sign-in page must render Clerk's form with
**no "Development mode" badge**, and sign-up must show the "I agree to the
Terms of Service and Privacy Policy" checkbox. Sign in and confirm `/continue`
routes you to the right place — that page asks the backend what the account is,
so it is the single best end-to-end proof that the JWT template in step 4 is
correct.
