# Deploying RentManager (backend, web, mobile) — staging first

> **Step-by-step runbook: [`deploy/README.md`](../deploy/README.md)** (server,
> Clerk, `.env`, `./deploy.sh`, backups). This page is the settings reference.

Verified 2026-09-15: the backend boots under the `prod` profile with **only**
a database, `JWT_SECRET` and `DARAJA_CREDENTIALS_ENCRYPTION_KEY` set — no
Daraja, Cloudinary, SMS or email credentials. Health is UP, public mobile
config answers, protected endpoints return 401, and M-Pesa callbacks return
404 while no callback secret is configured.

## 1. Settings you generate yourself (required — the app refuses to start without them)

| Variable | How to create | Notes |
|---|---|---|
| `JWT_SECRET` | `openssl rand -hex 32` | minimum 32 characters, enforced at startup |
| `DARAJA_CREDENTIALS_ENCRYPTION_KEY` | `openssl rand -base64 32` | encrypts landlords' Daraja credentials at rest. **Back it up; never rotate casually** — rows encrypted with it become unreadable without it |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | managed PostgreSQL 16 | defaults point at localhost dev credentials; always set them. Flyway migrates on boot (V1–V102) |
| `CORS_ALLOWED_ORIGINS` | the web app's https origin | default is `http://localhost:3000` |
| `SPRING_PROFILES_ACTIVE` | `prod` | already the default |

## 2. Identity (Clerk) — needed even for testing

Nobody can sign in without it. A Clerk **development** instance (`pk_test_…`)
is enough for staging; swap to a production instance at launch.

- Backend: `CLERK_JWKS_URL` (**set it explicitly** — the default points at a
  development Clerk instance), `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SECRET`.
- Clerk dashboard: JWT template named `backend` with the `tenant_id` claim;
  webhook to `/api/v1/webhooks/clerk` (user deletion sync).
- Web: `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`,
  `CLERK_WEBHOOK_SIGNING_SECRET`.
- Mobile: `EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY`; enable the native app in Clerk.

## 3. Providers that can be configured later

Each is off or fails closed at the point of use until configured; nothing
else is affected.

| Provider | Variables | Until configured |
|---|---|---|
| M-Pesa Daraja (platform) | `DARAJA_CONSUMER_KEY`, `DARAJA_CONSUMER_SECRET`, `DARAJA_SHORT_CODE`, `DARAJA_PASSKEY`, `DARAJA_BASE_URL`, `DARAJA_CALLBACK_SECRET`, `DARAJA_*_CALLBACK_URL` | STK pushes fail; all callbacks are rejected (blank secret never matches). Landlords' own Daraja credentials are entered in-app per organisation |
| Cloudinary | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` (or the Integrations console) | property/repair photo upload returns an error; everything else works |
| Africa's Talking SMS | `AT_ENABLED=true`, `AT_API_KEY`, `AT_USERNAME`, `AT_SENDER_ID` | SMS disabled (`AT_ENABLED=false` default) |
| Email | `NOTIFICATION_EMAIL_ENABLED=true` + mail settings | email disabled |
| Expo push | `PUSH_EXPO_ACCESS_TOKEN` (only if enhanced push security is on) | push works without it; needs EAS FCM/APNs credentials on the mobile side |
| Sentry | `EXPO_PUBLIC_SENTRY_DSN`, `NEXT_PUBLIC_SENTRY_DSN` | no crash reporting |
| Stripe (web) | `STRIPE_*` | optional in web config validation |

## 4. Web (Next.js)

Required: `BACKEND_URL` (production build refuses to start without it),
`NEXT_PUBLIC_API_URL` (including `/api/v1`), `NEXT_PUBLIC_APP_ENV=production`,
`NEXT_PUBLIC_APP_NAME`, `NEXT_PUBLIC_APP_URL`, Clerk keys above.
`next build` passes locally.

## 5. Mobile

See `rentmanager-mobile/docs/mobile/release.md`. For testing without store
accounts: an EAS `staging` build (internal APK) pointed at the staging API.

## Health and probes

- Liveness/readiness: `GET :8081/actuator/health` (management port,
  `MANAGEMENT_PORT`). Do not expose 8081 publicly.
- Smoke: `GET /api/v1/public/mobile/config` → 200;
  `GET /api/v1/users/me/access` without a token → 401.
