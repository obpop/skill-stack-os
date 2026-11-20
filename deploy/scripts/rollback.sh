#!/bin/sh
set -eu

previous=${1:?Usage: rollback.sh PREVIOUS_RELEASE_VERSION}
case "$previous" in (*[!A-Za-z0-9._-]*|'') echo "Invalid release version" >&2; exit 1;; esac

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
env_file=${SKILL_STACK_ENV_FILE:-$root_dir/deploy/env.production}
compose_file="$root_dir/compose.production.yml"
base_url=${SKILL_STACK_BASE_URL:?Set SKILL_STACK_BASE_URL}

echo "Application rollback does not reverse Flyway migrations. Confirm the previous image is schema-compatible."
export RELEASE_VERSION=$previous
if docker compose --env-file "$env_file" -f "$compose_file" ps --status running --services | grep -qx database; then
  echo "Creating and verifying an encrypted pre-rollback backup"
  sh "$root_dir/deploy/scripts/backup.sh"
fi
docker compose --env-file "$env_file" -f "$compose_file" pull frontend backend
docker compose --env-file "$env_file" -f "$compose_file" up -d --wait frontend backend edge
sh "$root_dir/deploy/scripts/smoke.sh" "$base_url"
echo "Rollback to $previous is healthy"
