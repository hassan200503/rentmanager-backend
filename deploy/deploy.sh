#!/usr/bin/env bash
# Build and (re)start the stack, then prove it is serving.
# Run from anywhere on the server:  ./deploy/deploy.sh
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "deploy/.env is missing. Copy deploy/.env.example and fill in the REQUIRED values." >&2
  exit 1
fi
chmod 600 .env
mkdir -p backups

# Read only what this script needs. .env is compose syntax, not shell:
# values may contain spaces (BACKEND_JAVA_OPTS) and inline comments.
env_value() {
  grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- \
    | sed -E "s/[[:space:]]+#.*\$//; s/^[\"']//; s/[\"']\$//; s/[[:space:]]+\$//"
}
RELEASE="$(env_value RELEASE)"
APP_DOMAIN="$(env_value APP_DOMAIN)"
API_DOMAIN="$(env_value API_DOMAIN)"

echo "==> Validating compose configuration"
docker compose config --quiet

echo "==> Building images (release ${RELEASE:-latest})"
docker compose build --pull

echo "==> Starting"
docker compose up -d --remove-orphans

echo "==> Waiting for the backend to become healthy (migrations run on first start)"
for i in $(seq 1 60); do
  status="$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q backend)" 2>/dev/null || echo starting)"
  if [[ "$status" == "healthy" ]]; then break; fi
  if [[ "$i" == 60 ]]; then
    echo "Backend did not become healthy. Last log lines:" >&2
    docker compose logs --tail 80 backend >&2
    exit 1
  fi
  sleep 5
done

echo "==> Smoke tests"
fail=0
check() { # name expected url [extra curl args...]
  local name="$1" expected="$2" url="$3" code
  shift 3
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$@" "$url" || true)"
  if [[ "$code" == "$expected" ]]; then echo "  ok   $name ($code)"; else echo "  FAIL $name expected $expected got $code" >&2; fail=1; fi
}
check "public mobile config"          200 "https://${API_DOMAIN}/api/v1/public/mobile/config"
check "protected API without token"   401 "https://${API_DOMAIN}/api/v1/users/me/access"
check "API docs not public"           404 "https://${API_DOMAIN}/v3/api-docs"
check "actuator not public"           404 "https://${API_DOMAIN}/actuator/health"
check "callback rejects wrong secret" 404 "https://${API_DOMAIN}/api/v1/public/rent-ledger/mpesa/callback/not-the-secret" \
      -X POST -H 'Content-Type: application/json' -d '{}'
check "web app home"                  200 "https://${APP_DOMAIN}/"
check "web proxies the API"           200 "https://${APP_DOMAIN}/api/v1/public/mobile/config"

if [[ "$fail" != 0 ]]; then
  echo "Smoke tests failed. The stack is running; inspect with: docker compose logs -f" >&2
  exit 1
fi
echo "==> Deployed ${RELEASE:-latest}"
