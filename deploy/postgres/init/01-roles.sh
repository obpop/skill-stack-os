#!/bin/sh
set -eu

case "${DATABASE_APP_USER:-}" in (*[!A-Za-z0-9_]*) echo "DATABASE_APP_USER contains invalid characters" >&2; exit 1;; esac
case "${DATABASE_MIGRATION_USER:-}" in (*[!A-Za-z0-9_]*) echo "DATABASE_MIGRATION_USER contains invalid characters" >&2; exit 1;; esac

test -n "${DATABASE_APP_USER:-}"
test -n "${DATABASE_APP_PASSWORD:-}"
test -n "${DATABASE_MIGRATION_USER:-}"
test -n "${DATABASE_MIGRATION_PASSWORD:-}"

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_user="$DATABASE_APP_USER" \
  --set=app_password="$DATABASE_APP_PASSWORD" \
  --set=migration_user="$DATABASE_MIGRATION_USER" \
  --set=migration_password="$DATABASE_MIGRATION_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'migration_user', :'migration_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'migration_user')\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'app_user')\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'migration_user')\gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user')\gexec
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'migration_user')\gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'migration_user', :'app_user')\gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', :'migration_user', :'app_user')\gexec
SQL
