#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
compose_file=${SKILL_STACK_COMPOSE_FILE:-$root_dir/compose.production.yml}
database_service=${SKILL_STACK_DATABASE_SERVICE:-database}
env_file=${SKILL_STACK_ENV_FILE:-$root_dir/deploy/env.production}
key_file=${BACKUP_ENCRYPTION_KEY_FILE:?Set BACKUP_ENCRYPTION_KEY_FILE}
destination=${OFFSITE_BACKUP_DIR:?Set OFFSITE_BACKUP_DIR to a mounted directory on a different host or storage account}
retention_days=${BACKUP_RETENTION_DAYS:-14}

command -v docker >/dev/null
command -v openssl >/dev/null
command -v sha256sum >/dev/null
test -f "$env_file"
test -f "$key_file"
test -d "$destination"
case "$database_service" in (''|-*|*[!A-Za-z0-9_.-]*) echo "SKILL_STACK_DATABASE_SERVICE is invalid" >&2; exit 1;; esac
case "$retention_days" in (*[!0-9]*|'') echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2; exit 1;; esac

work_dir=$(mktemp -d)
case "$work_dir" in (/tmp/*|/var/tmp/*) ;; (*) echo "Unexpected temporary directory" >&2; exit 1;; esac
cleanup() { rm -rf -- "$work_dir"; }
trap cleanup EXIT INT TERM
umask 077

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
plain="$work_dir/skill-stack-$timestamp.dump"
encrypted="$work_dir/skill-stack-$timestamp.dump.enc"

docker compose --env-file "$env_file" -f "$compose_file" exec -T "$database_service" sh -c \
  'pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$plain"
test -s "$plain"

openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -in "$plain" -out "$encrypted" -pass "file:$key_file"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$encrypted" -pass "file:$key_file" | \
  docker compose --env-file "$env_file" -f "$compose_file" exec -T "$database_service" pg_restore --list >/dev/null

final="$destination/$(basename "$encrypted")"
mv -- "$encrypted" "$final"
(cd "$destination" && sha256sum "$(basename "$final")" > "$(basename "$final").sha256")
find "$destination" -maxdepth 1 -type f -name 'skill-stack-*.dump.enc' -mtime "+$retention_days" -delete
find "$destination" -maxdepth 1 -type f -name 'skill-stack-*.dump.enc.sha256' -mtime "+$retention_days" -delete
echo "Encrypted backup written and verified: $final"
