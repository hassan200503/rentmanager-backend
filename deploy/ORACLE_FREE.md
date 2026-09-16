# RentManager at zero cost — Oracle Cloud Always Free

> **Needs a card** (US$1 authorised and released, never charged; prepaid and
> virtual cards are refused). With no card at all, use
> [FREE_NO_CARD.md](FREE_NO_CARD.md) instead — Render, Neon and Netlify, all
> cardless. Come back here when a card is available: this path is stronger
> (always on, 12 GB RAM, no 9-minute cold starts).

This is the free path if you have a card. Do these steps first, then continue with
[README.md](README.md) from **§2**. Limits were checked on 2026-09-15; free
tiers change, so re-check anything that looks different.

## What is free, and what is not

| Need | Free service | Limit to know |
|---|---|---|
| Server (backend, web, database, HTTPS) | Oracle Cloud Always Free, Ampere A1 | 2 OCPU, 12 GB RAM, 200 GB disk, 10 TB/month traffic. Reclaimed if idle (see §6) |
| Domain names + HTTPS | DuckDNS subdomains + Let's Encrypt (Caddy) | Names look like `rentmanager-app.duckdns.org` |
| Sign-in | Clerk **development** instance | **100 users maximum**, "development mode" badge |
| Android app | EAS Build free plan, APK installed directly | 15 Android builds a month, slow queue |
| Push notifications | Expo Push Service | free |
| Code and CI | GitHub | private repos get 2,000 Actions minutes a month |
| Off-server backups | Google Drive (15 GB) via rclone, encrypted | — |
| Uptime alerts | UptimeRobot free | 5-minute checks |
| M-Pesa | Daraja sandbox; landlords connect their own Paybill in the app | free for the platform |

**Not free (only when you grow):** a real domain (about KSh 1,000–1,500 a year for `.co.ke`),
which a production Clerk instance needs beyond 100 users; Google Play's one-time US$25 fee;
Apple's US$99 a year for iOS (skip iOS until then); SMS (per message, so the app uses free push
instead).

---

## 1. Create the Oracle account (15 min)

1. Sign up at signup.oraclecloud.com. A card is used for identity only: US$1 is authorised and
   released, and nothing is charged on a Free Tier account. Prepaid, virtual and PIN-only cards
   are refused.
2. **Home region is permanent** and Always Free compute only runs there. Pick
   **South Africa Central (Johannesburg)**, the closest to Kenya, or an EU region if Johannesburg
   shows no A1 capacity.
3. Stay on the **Free Tier**. Do not upgrade to Pay As You Go unless you set a budget alert first,
   because paid resources can then be created by mistake.
4. **Billing → Budgets:** create a US$1 budget with an email alert anyway. It costs nothing and
   catches mistakes.

## 2. Create the server (10 min)

**Compute → Instances → Create instance**:

- **Image:** Canonical Ubuntu 24.04 (the aarch64 build that matches the shape).
- **Shape:** Ampere → `VM.Standard.A1.Flex`, **2 OCPU, 12 GB memory** (shown as "Always Free-eligible").
- **Networking:** create a new VCN with a public subnet, and **assign a public IPv4 address**.
- **SSH keys:** upload your public key (`ssh-keygen -t ed25519` on your computer if you have none).
- **Boot volume:** 100 GB (the free allowance is 200 GB in total).

If you get "Out of capacity": try another availability domain, try again later, or temporarily
create it with 1 OCPU / 6 GB and resize once capacity frees up.

**Open the ports in Oracle's network** (Networking → your VCN → Security Lists → Default → Add
ingress rules), all with source `0.0.0.0/0`:
- TCP 80
- TCP 443
- UDP 443

## 3. Open the ports on the server itself (5 min)

Oracle's Ubuntu images ship iptables rules that reject everything except SSH. Opening the Security
List is not enough. **Do not enable `ufw`** on these images; it fights those rules.

```bash
ssh ubuntu@<public-ip>
sudo apt update && sudo apt -y upgrade && sudo apt -y install unattended-upgrades fail2ban git
POS=$(sudo iptables -L INPUT --line-numbers | awk '/REJECT/ {print $1; exit}')
sudo iptables -I INPUT "$POS" -p tcp -m multiport --dports 80,443 -m conntrack --ctstate NEW -j ACCEPT
sudo iptables -I INPUT "$POS" -p udp --dport 443 -m conntrack --ctstate NEW -j ACCEPT
sudo netfilter-persistent save
```

Also set `PasswordAuthentication no` in `/etc/ssh/sshd_config`, then run `sudo systemctl restart ssh`.

Install Docker Engine for Ubuntu (arm64 is supported) from docs.docker.com/engine/install/ubuntu,
then run `sudo usermod -aG docker ubuntu` and log in again.

**Check:** `docker run --rm hello-world` works.

## 4. Free domain names (5 min)

1. Sign in at duckdns.org with GitHub or Google.
2. Create two subdomains, e.g. `rentmanager-app` and `rentmanager-api`, and set both to the
   server's public IP.
3. In `deploy/.env`:
   ```
   APP_DOMAIN=rentmanager-app.duckdns.org
   API_DOMAIN=rentmanager-api.duckdns.org
   ```

Caddy obtains the certificates on first start once DNS resolves. **Check:**
`nslookup rentmanager-app.duckdns.org` returns your IP.

## 5. Get the code onto the server (5 min)

For private repos, use a read-only **deploy key**, not your password:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/rentmanager_deploy -N ""
cat ~/.ssh/rentmanager_deploy.pub
```

Add that public key under GitHub → each repo → Settings → Deploy keys (read-only, leave write
access off). Then:

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/rentmanager_deploy
EOF
mkdir -p ~/rentmanager && cd ~/rentmanager
git clone git@github.com:<you>/rentmanager-backend.git
git clone git@github.com:<you>/rentmanager-frontend.git
```

Now continue with **[README.md](README.md) §3 (Clerk, using a development instance) and §4 (`.env`)**.
In `.env`, **use the ORACLE sizing block** at the bottom, then run `./deploy.sh` (§5).

## 6. Keep Oracle from reclaiming the server

Oracle reclaims an Always Free instance when, over 7 days, CPU (95th percentile), network **and**
memory all stay below 20%. The ORACLE sizing block pre-allocates a 3 GB heap and a 1 GB database
cache, about 4–5 GB of 12 GB. That keeps memory above the line and also makes the app faster.

**Check it after deploying:** `free -g` should show at least 3 GB used. If it shows less, the
ORACLE block isn't active in `.env`.

## 7. Encrypted off-server backups (15 min, free)

Database dumps contain renters' personal and payment data, so they are encrypted before they leave
the server. They go to a different company, so an Oracle account problem can't take the backups
with it.

```bash
sudo apt -y install rclone
rclone config    # new remote "gdrive": type drive, and answer "n" to auto config —
                 # it prints an `rclone authorize "drive"` command to run on your own computer
rclone config    # new remote "backup": type crypt, remote gdrive:rentmanager-backups,
                 # generate strong passwords and STORE THEM IN YOUR PASSWORD MANAGER
crontab -e
# add this line:
# 30 3 * * * rclone copy /home/ubuntu/rentmanager/rentmanager-backend/deploy/backups backup: --max-age 48h
```

**Check:** the next day, `rclone ls backup:` lists a `.dump` file.

Rehearse a restore once (README §7). An untested backup is only a hope.

## 8. Free monitoring (5 min)

In UptimeRobot, add two HTTPS monitors with alerts by email or the mobile app:
- `https://<API_DOMAIN>/api/v1/public/mobile/config`
- `https://<APP_DOMAIN>/`

## 9. Android app for testers (free)

Follow README §8 with `EXPO_PUBLIC_API_BASE_URL=https://<API_DOMAIN>/api/v1`. The staging profile
builds an APK you share as a link; testers allow "install unknown apps" once.
