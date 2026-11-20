#!/bin/sh
set -eu

backup=${1:?Usage: restore-verify.sh BACKUP.dump.enc}
key_file=${BACKUP_ENCRYPTION_KEY_FILE:?Set BACKUP_ENCRYPTION_KEY_FILE}
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
compose_file="$root_dir/deploy/compose.recovery.yml"

test -f "$backup"
test -f "$key_file"
command -v docker >/dev/null
command -v openssl >/dev/null

RECOVERY_DB_PASSWORD=$(openssl rand -hex 24)
export RECOVERY_DB_PASSWORD
project_name="skill-stack-recovery-$$"

cleanup() {
  docker compose -p "$project_name" -f "$compose_file" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker compose -p "$project_name" -f "$compose_file" up -d --wait database
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$backup" -pass "file:$key_file" | \
  docker compose -p "$project_name" -f "$compose_file" exec -T database \
    pg_restore --username recovery_admin --dbname skillstack_recovery --no-owner --no-acl --exit-on-error

docker compose -p "$project_name" -f "$compose_file" exec -T database psql \
  --username recovery_admin --dbname skillstack_recovery --set=ON_ERROR_STOP=1 <<'SQL'
DO $$
BEGIN
  IF (SELECT max(version::integer) FROM flyway_schema_history WHERE success) < 7 THEN
    RAISE EXCEPTION 'Flyway schema is older than V7';
  END IF;
  IF EXISTS (SELECT 1 FROM graph_edge e JOIN graph_node s ON s.id=e.source_node_id JOIN graph_node t ON t.id=e.target_node_id
             WHERE e.owner_id<>s.owner_id OR e.owner_id<>t.owner_id) THEN
    RAISE EXCEPTION 'Cross-owner graph edge detected';
  END IF;
  IF EXISTS (SELECT 1 FROM project p JOIN graph_node n ON n.id=p.graph_node_id WHERE p.owner_id<>n.owner_id) THEN
    RAISE EXCEPTION 'Project graph ownership mismatch detected';
  END IF;
  IF EXISTS (SELECT 1 FROM background_job j JOIN recommendation_result r ON r.id=j.result_reference_id
             WHERE j.owner_id<>r.owner_id) THEN
    RAISE EXCEPTION 'Background result ownership mismatch detected';
  END IF;
END $$;
SELECT 'users=' || count(*) FROM app_user;
SELECT 'nodes=' || count(*) FROM graph_node;
SELECT 'projects=' || count(*) FROM project;
SELECT 'recommendations=' || count(*) FROM recommendation_result;
SQL

echo "Isolated restore and ownership verification passed"
