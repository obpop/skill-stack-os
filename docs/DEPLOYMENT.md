# Skill Stack deployment

This document covers two deployment shapes:

1. **Local development** on your machine. Most readers want this.
2. **Self-hosted on a VPS** if you want to run the full product publicly.

The showcase website (landing, fictional demo, thesis, case study) is a separate project and is not part of this repository.

## Local development

Prerequisites:

- Node 20.9+
- Java 21
- Maven 3.9+
- Docker Desktop (for PostgreSQL)

Setup:

```sh
git clone https://github.com/oliverberggren/skill-stack.git
cd skill-stack
cp .env.example .env.local
docker compose up -d postgres
```

Terminal 1, backend:

```sh
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Terminal 2, frontend:

```sh
npm install
npm run dev
```

To enable AI locally, copy `backend/.env.example` to `backend/.env` and fill in the key and budgets; the `local` profile reads that file at startup.

Open `http://localhost:3000`. The `local` Spring profile exposes a signed dev-login endpoint, so you can enter any name and sign in. PostgreSQL runs on `localhost:5432` with the credentials in `.env.example`.

## Self-hosted VPS topology

A single small VPS is enough:

```text
Internet
  -> Caddy :80/:443 (automatic TLS, request-size limit, security headers)
     -> Next.js frontend :3000 (private edge network)
     -> Spring Boot API :8080 (private edge + data networks)
        -> PostgreSQL 16 :5432 (private internal data network only)
        -> optional OpenAI API (disabled and USD 0 by default)

Scheduled host timer
  -> encrypted pg_dump
  -> operator-mounted off-site storage in a different account or host
```

One Spring process serves both the API and the in-process durable job worker. No Redis, no RabbitMQ, no Kubernetes, no always-on metrics stack.

The Compose limits reserve up to 1,792 MiB across Caddy, Next.js, Spring, and PostgreSQL before Docker and host overhead. A 4 GB host is the minimum. 8 GB is comfortable.

## Host prerequisites

- One supported Linux host, Docker Engine, and Compose v2.
- DNS A/AAAA records for one application hostname.
- Inbound firewall rules: TCP 22 from your operator allowlist, TCP 80/443 for everyone, optional UDP 443 for HTTP/3. Do not expose 3000, 5432, or 8080.
- Automatic security updates or a documented patch window.
- A non-root operator with narrowly scoped Docker access. Treat Docker access as root-equivalent.
- If you want off-site backups, a path mounted to a different storage account or host, plus a root-readable backup passphrase file stored outside the repository and the database.

## First deployment

1. Pick your host, domain, and off-site storage.
2. Install Docker from the OS vendor repository. Enable the host firewall.
3. Check out the reviewed release into a fixed path like `/opt/skill-stack`.
4. Copy `deploy/env.production.example` to `deploy/env.production`. Generate independent random values for every database role. Set file mode `600`.
5. Copy a backup passphrase to a host-only file outside the checkout, mode `600`. Export:

   ```sh
   export SKILL_STACK_ENV_FILE=/opt/skill-stack/deploy/env.production
   export SKILL_STACK_BASE_URL=https://your-domain.example.com
   export BACKUP_ENCRYPTION_KEY_FILE=/etc/skill-stack/backup.pass
   export OFFSITE_BACKUP_DIR=/mnt/skill-stack-offsite
   ```

6. Create an Auth0 application and API, then fill in the `AUTH0_*` values in `deploy/env.production` (see `.env.example`).
7. Validate configuration without printing resolved secrets:

   ```sh
   docker compose --env-file deploy/env.production -f compose.production.yml config --quiet
   ```

8. Authenticate the host to the selected container registry with a least-privilege pull token.
9. Release by immutable commit SHA:

   ```sh
   sh deploy/scripts/release.sh <commit-sha>
   ```

10. Confirm the public readiness endpoint, landing/auth entry, unauthenticated API rejection, version labels, logs, and disk headroom. Run a backup and isolated restore before using the deployment.

The first release starts an empty database and applies Flyway V1-V8. Later releases take an encrypted backup before images are replaced.

## Release and rollback

Normal release:

```sh
sh deploy/scripts/release.sh <new-commit-sha>
```

Application rollback:

```sh
sh deploy/scripts/rollback.sh <previous-known-good-sha>
```

Both operations take and verify an encrypted backup when the database is already running. A rollback changes application images only. Flyway migrations are forward-only. Never run an older image unless its schema compatibility is known. Restore a backup into isolation first when a migration may be involved.

## Routine operations

```sh
# Health and current service state
docker compose --env-file deploy/env.production -f compose.production.yml ps
sh deploy/scripts/healthcheck.sh

# Current immutable image/version metadata
docker compose --env-file deploy/env.production -f compose.production.yml images
docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' skill-stack-backend-1

# Recent capped logs
docker compose --env-file deploy/env.production -f compose.production.yml logs --since 30m --tail 300 backend

# Restart one service
docker compose --env-file deploy/env.production -f compose.production.yml restart backend

# Readiness check
curl --fail --silent https://your-domain.example.com/actuator/health/readiness
```

For failed background jobs, use the owner-scoped UI retry after correcting the cause. Expired leases recover automatically.

## Monitoring

- The public readiness endpoint checks application readiness, database connectivity, disk space, and oldest pending job age.
- Container logs use JSON rotation at 10 MiB times three files. Spring production logs are ECS JSON. Request payloads, Authorization headers, raw import text, prompts, tokens, and secrets must not be added to logs.
- Install the provided health-check and backup systemd timers after reviewing their paths and environment files.
- Review host CPU, memory, disk, PostgreSQL connections, backup age, failed/stuck jobs, HTTP error/latency counters, and AI reservation totals periodically.

## Decommission or migration

Stop new writes, create and verify a final encrypted backup, restore it into the new isolated environment, run smoke/auth/ownership checks, change DNS, and retain the prior host only for the approved rollback window. Then destroy the old host and revoke its credentials. A server snapshot is not a database backup.
