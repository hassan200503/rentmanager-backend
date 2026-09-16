#!/usr/bin/env bash
# Turn a Neon connection string into the three values the API needs.
#
# Run it on your own machine and paste the Neon string when prompted — it is
# read without echoing, and nothing is written to disk or shown in scrollback
# except the values you copy into Render.
#
#   ./neon-to-env.sh
set -euo pipefail

printf 'Paste the Neon connection string (input hidden), then press Enter:\n' >&2
read -r -s CONN
printf '\n' >&2

if [[ -z "${CONN:-}" ]]; then
  echo "Nothing pasted." >&2
  exit 1
fi

# postgresql://user:password@host/db?params
if [[ ! "$CONN" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^/]+)/([^?]+)(\?(.*))?$ ]]; then
  echo "That does not look like a Neon connection string (expected postgresql://user:password@host/db?sslmode=require)." >&2
  exit 1
fi

USER="${BASH_REMATCH[2]}"
PASS="${BASH_REMATCH[3]}"
HOST="${BASH_REMATCH[4]}"
DB="${BASH_REMATCH[5]}"
PARAMS="${BASH_REMATCH[7]:-sslmode=require}"

case "$PARAMS" in
  *sslmode=*) ;;
  *) PARAMS="$PARAMS&sslmode=require" ;;
esac

cat <<OUT
Copy these into Render (Environment):

DB_URL=jdbc:postgresql://$HOST/$DB?$PARAMS
DB_USERNAME=$USER
DB_PASSWORD=$PASS

Notes:
  - DB_URL carries no username or password. Render holds those separately.
  - sslmode=require is kept: Neon refuses plaintext connections.
OUT
