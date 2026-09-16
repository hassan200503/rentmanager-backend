# Deploying RentManager tonight — runbook

One Linux server runs everything: PostgreSQL, the backend, the web app, Caddy
(automatic HTTPS) and nightly backups. The mobile app is built with EAS and
talks to `https://API_DOMAIN`.

Allow about 90 minutes the first time. Every step says how to confirm it worked.

> **No budget?** Two free paths, neither with a bill:
> - **No card at all:** [FREE_NO_CARD.md](FREE_NO_CARD.md) — Render + Neon +
>   Netlify. Replaces §0–§2 and §5 entirely (no Docker Compose, no server).
> - **Card available** (for identity only): [ORACLE_FREE.md](ORACLE_FREE.md) —
>   a free always-on Oracle server that runs this whole Compose stack.

---

## 0. What you need before starting

- [ ] A server running **Ubuntu 24.04** with at least **2 vCPU, 4 GB RAM and 40 GB disk**,
      in a Nairobi or EU region. Building the images needs the RAM; see §9.
- [ ] A domain, with two DNS **A records** pointing at the server:
      `app.<domain>` (web) and `api.<domain>` (API). Create them first, because DNS takes time.
- [ ] A Clerk instance. A development instance (`pk_test_…`) is fine for staging.
- [ ] Both repositories pushed to GitHub (`rentmanager-backend`, `rentmanager-frontend`).
- [ ] A password manager for the generated secrets.

## 1. Harden the server (10 min)

```bash
adduser deploy && usermod -aG sudo deploy
# copy your SSH key to deploy, then in /etc/ssh/sshd_config:
#   PasswordAuthentication no
#   PermitRootLogin no
sudo systemctl restart ssh
sudo apt update && sudo apt -y upgrade && sudo apt -y install unattended-upgrades ufw fail2ban git
sudo ufw default deny incoming && sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw allow 443/udp
sudo ufw enable
```

Install Docker Engine and the compose plugin from Docker's apt repository
(docs.docker.com/engine/install/ubuntu), then run `sudo usermod -aG docker deploy` and log in again.

> Docker writes its own iptables rules, so a published port bypasses ufw.
> The compose file publishes **only** 80 and 443, which is why Postgres and
> the backend have no `ports:` entries. Do not add any.

**Check:** `docker compose version` prints v2.x.

## 2. Get the code (2 min)

```bash
mkdir -p ~/rentmanager && cd ~/rentmanager
git clone https://github.com/<you>/rentmanager-backend.git
git clone https://github.com/<you>/rentmanager-frontend.git
```

The two directories must sit side by side; the compose file builds the web image from `../../rentmanager-frontend`.

## 3. Configure Clerk (15 min)

Clerk dashboard, on the instance you will use:

1. **JWT template** named exactly `backend`, with these claims:
   ```json
   {
     "tenant_id": "{{org.id}}",
     "email": "{{user.primary_email_address}}",
     "platformRole": "{{user.public_metadata.platformRole}}",
     "userType": "{{user.public_metadata.userType}}"
   }
   ```
2. **Session token** custom claims: `{"tenant_id": "{{org.id}}", "userType": "{{user.public_metadata.userType}}"}`.
3. **Organizations** enabled (each landlord is an organisation).
4. **Webhooks**, both subscribed to `user.created`, `user.updated` and `user.deleted`:
   - `https://api.<domain>/api/v1/webhooks/clerk` → its secret is `CLERK_WEBHOOK_SECRET`
   - `https://app.<domain>/api/webhooks/clerk` → its secret is `CLERK_WEB_WEBHOOK_SIGNING_SECRET`
5. **Domains / allowed origins:** add `https://app.<domain>`.
6. **Native applications:** enable them for the mobile bundle IDs (see mobile `docs/mobile/release.md`).
7. **Your own admin account:** after you first sign up, set public metadata
   `{"platformRole": "OWNER"}` on your user, then sign out and back in.

`CLERK_JWKS_URL` is `https://<frontend-api-host>/.well-known/jwks.json`. The host is shown on
Clerk's API keys page; for a development instance it looks like `xxx.clerk.accounts.dev`.

## 4. Fill in the environment (10 min)

```bash
cd ~/rentmanager/rentmanager-backend/deploy
cp .env.example .env && chmod 600 .env
openssl rand -hex 24      # POSTGRES_PASSWORD
openssl rand -hex 32      # JWT_SECRET
openssl rand -base64 32   # DARAJA_CREDENTIALS_ENCRYPTION_KEY
nano .env
```

Fill in every value marked **REQUIRED**. Store all three generated values in your password manager
now. **If `DARAJA_CREDENTIALS_ENCRYPTION_KEY` is lost, every landlord's stored M-Pesa credentials
become unreadable.**

Leave the provider sections (Daraja, Cloudinary, Africa's Talking, email) empty for now. Each of
those features returns a clear error until it is configured, and nothing else is affected.

## 5. Deploy (15–25 min, mostly building)

```bash
./deploy.sh
```

The script validates the configuration, builds both images, starts the stack, waits for the
backend to report healthy (migrations run on first start), then checks from outside:

| Check | Expected |
|---|---|
| public mobile config | 200 |
| protected API without a token | 401 |
| `/v3/api-docs` | 404 |
| `/actuator/health` (public) | 404 |
| M-Pesa callback with the wrong secret | 404 |
| web home page | 200 |
| web → API proxy | 200 |

If the backend refuses to start, `docker compose logs backend` lists every unsafe setting by name
(the startup guard never prints values). Fix `.env` and run `./deploy.sh` again.

## 6. First-run acceptance (20 min, in a browser and on a phone)

1. Open `https://app.<domain>`. It should load with a padlock.
2. Sign up as a landlord, complete onboarding, and add a property and a unit.
3. Create a lease for a test renter (use a second email address you control).
4. Sign in as that renter in a private window. The portal should show the lease and balance.
5. Record a cash payment as the landlord. The renter's balance should update.
6. Submit a repair request as the renter. The landlord should see it and move it to In progress.
7. Mobile: build the staging APK (§8), sign in as both people and repeat steps 5–6.
8. Sign out on the phone, sign in as the other person, and confirm no data from the first person is shown.

## 7. Backups (do not skip)

- The `backup` container writes `deploy/backups/rentmanager-<UTC>.dump` at start and then every
  24 hours, keeping 14 days.
- **Copy the backups off this server.** A backup on the same disk dies with the disk. Start with a
  nightly `rclone copy` or `rsync` to object storage, or at minimum your provider's volume snapshots.
- **Test a restore before you have users**, into a scratch container:
  ```bash
  docker run --rm -d --name restore-test -e POSTGRES_PASSWORD=x postgres:16-alpine
  docker cp backups/<file>.dump restore-test:/tmp/r.dump
  docker exec restore-test sh -c 'sleep 5; createdb -U postgres rm && pg_restore -U postgres -d rm --no-owner /tmp/r.dump && psql -U postgres -d rm -c "select count(*) from flyway_schema_history"'
  docker rm -f restore-test
  ```

## 8. Mobile staging build

Set these in the Expo project's **preview** environment (the `staging` build profile uses it):
- `EXPO_PUBLIC_API_BASE_URL=https://api.<domain>/api/v1`
- `EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY`
- `EXPO_PUBLIC_CLERK_JWT_TEMPLATE=backend`
- `EXPO_PUBLIC_WEB_APP_URL=https://app.<domain>`

Then run `npx eas build --profile staging --platform android` and install the APK on a phone.

## 9. Operating it

| Task | Command |
|---|---|
| Follow logs | `docker compose logs -f backend web` |
| Status | `docker compose ps` |
| Deploy a new version | `git -C ../ pull && git -C ../../rentmanager-frontend pull`, set `RELEASE` in `.env`, then `./deploy.sh` |
| Roll back | `git checkout <previous tag>` in both repos, then `./deploy.sh`. Migrations only move forward, so roll back code, never the schema |
| Database shell | `docker compose exec postgres psql -U rentmanager` |
| Restart one service | `docker compose restart backend` |

**Uptime monitoring:** point a free external monitor (UptimeRobot, Better Stack) at
`https://api.<domain>/api/v1/public/mobile/config` and `https://app.<domain>/`, with alerts
going to your phone.

**Low-memory server:** if the build is killed (exit 137) on a 2–4 GB machine, add swap
(`sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`)
or build the images in CI and pull them instead.

## 10. Adding providers later

Add the values to `.env` and run `./deploy.sh`. Nothing else changes.

- **M-Pesa:** generate `DARAJA_CALLBACK_SECRET` with `openssl rand -hex 24` (it must be at least 32
  characters, which the startup guard enforces). Each callback URL ends with that secret, as shown in
  `.env.example`. Landlords can also enter their own Paybill credentials in the app, which is the
  compliant default: rent goes straight to the landlord.
- **Cloudinary:** photos use private (`authenticated`) delivery; no public URL settings are needed.
- **SMS:** set `AT_ENABLED=true` together with the key, username and sender ID.

## Before inviting real users (not needed for tonight's staging)

- A privacy policy and terms page linked from the app and web. Kenya's Data Protection Act 2019
  also requires registering with the ODPC as a data controller.
- A production Clerk instance (`pk_live_`) on your domain. The web security policy picks up the new
  Clerk domain automatically at build time.
- Off-server backups running, and one restore tested.
- Live Daraja credentials, and a KSh 1 payment made end to end.
