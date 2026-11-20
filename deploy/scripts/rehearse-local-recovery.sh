#!/bin/sh
set -eu

# Local-only proof that the encrypted backup can be restored in isolation.
# Production still requires a genuinely independent off-site destination.
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
work_dir=$(mktemp -d)
case "$work_dir" in (/tmp/*|/var/tmp/*) ;; (*) echo "Unexpected temporary directory" >&2; exit 1;; esac
cleanup() { rm -rf -- "$work_dir"; }
trap cleanup EXIT INT TERM

key_file="$work_dir/backup.key"
# Git for Windows does not path-convert the value embedded in OpenSSL's
# `file:` passphrase argument, so use its native mixed path there.
if command -v cygpath >/dev/null 2>&1; then
  key_file=$(cygpath -m "$key_file")
fi
offsite_dir="$work_dir/offsite"
mkdir -p "$offsite_dir"
openssl rand -hex 32 > "$key_file"
chmod 600 "$key_file"

export SKILL_STACK_COMPOSE_FILE="$root_dir/docker-compose.yml"
export SKILL_STACK_DATABASE_SERVICE=postgres
export SKILL_STACK_ENV_FILE="$root_dir/.env.example"
export BACKUP_ENCRYPTION_KEY_FILE="$key_file"
export OFFSITE_BACKUP_DIR="$offsite_dir"

docker compose -f "$SKILL_STACK_COMPOSE_FILE" exec -T postgres pg_isready --username skillstack --dbname skillstack >/dev/null

backup_started=$(date +%s)
"$root_dir/deploy/scripts/backup.sh"
backup_finished=$(date +%s)

backup_file=$(find "$offsite_dir" -maxdepth 1 -type f -name 'skill-stack-*.dump.enc' | head -n 1)
test -n "$backup_file"
(cd "$offsite_dir" && sha256sum -c "$(basename "$backup_file").sha256")

restore_started=$(date +%s)
"$root_dir/deploy/scripts/restore-verify.sh" "$backup_file"
restore_finished=$(date +%s)

echo "Local recovery rehearsal passed: backup_seconds=$((backup_finished-backup_started)) restore_seconds=$((restore_finished-restore_started))"
