#!/bin/sh
# Nightly pg_dump (custom format, compressed) with local retention.
# Restore: pg_restore --clean --if-exists -d rentmanager <file>   (see README)
set -eu

RETENTION="${BACKUP_RETENTION_DAYS:-14}"

run_backup() {
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  tmp="/backups/.rentmanager-${stamp}.dump.partial"
  out="/backups/rentmanager-${stamp}.dump"
  if pg_dump --format=custom --compress=6 --no-owner --file="$tmp"; then
    mv "$tmp" "$out"
    echo "backup ok: $(basename "$out") $(du -h "$out" | cut -f1)"
  else
    rm -f "$tmp"
    echo "backup FAILED at ${stamp}" >&2
  fi
  find /backups -name 'rentmanager-*.dump' -mtime +"$RETENTION" -delete
}

# One backup at start (so a fresh deploy is covered), then daily.
run_backup
while true; do
  sleep 86400
  run_backup
done
