#!/bin/sh
set -eu

release=${1:?Usage: release.sh RELEASE_VERSION}
case "$release" in (*[!A-Za-z0-9._-]*|'') echo "Invalid release version" >&2; exit 1;; esac

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
env_file=${SKILL_STACK_ENV_FILE:-$root_dir/deploy/env.production}
compose_file="$root_dir/compose.production.yml"
base_url=${SKILL_STACK_BASE_URL:?Set SKILL_STACK_BASE_URL}

test -f "$env_file"
export RELEASE_VERSION=$release
if docker compose --env-file "$env_file" -f "$compose_file" ps --status running --services | grep -qx database; then
  echo "Creating and verifying an encrypted pre-release backup"
  sh "$root_dir/deploy/scripts/backup.sh"
fi
docker compose --env-file "$env_file" -f "$compose_file" pull frontend backend edge database
docker compose --env-file "$env_file" -f "$compose_file" up -d --wait --remove-orphans
sh "$root_dir/deploy/scripts/smoke.sh" "$base_url"
echo "Release $release is healthy"
