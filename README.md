# Skill Stack

> What skill should I build next, and why?

Skill Stack is a capability decision tool. It turns a person's existing skills, optional interests, goals, role, and constraints into one actionable next skill, useful alternatives, and concrete evidence they can inspect and build on.

The written explanation is the product. The graph is an optional lens on the same result.

This repository is the open-source app. It runs on your own machine. A separate showcase website hosts the fictional demo and the thesis.

## What makes it interesting

- **Text-first by design.** Every decision exists as readable text. Paths, written strategies, tradeoffs, alternatives, next skill, first action, proof. The graph is a visual lens, not the product.
- **Deterministic before AI.** Bounded SQL retrieval ranks candidate next skills with strict schemas, verified resources, and provenance. No external call is needed for the core loop.
- **Bounded, optional AI.** If enabled, the provider adapter has per-call reservations, monthly and cumulative budget ceilings, strict structured outputs, and a deterministic fallback. Disabled by default with zero budgets.
- **PostgreSQL before infrastructure.** Durable background jobs live in PostgreSQL with row claiming, leases, idempotency, and recovery. No broker, no cache service, no vector database.
- **Selective public proof.** Users can publish a sanitized snapshot of their stack to a slug or revocable share link. Edits withdraw the public version until republished. No social network, no indexing surprises.
- **Private by default.** Role, goals, city, imports, notes, rejected ideas, raw provider output, and token hashes are never in the public snapshot. Integration tests assert these absences.
- **One deployable system.** Next.js frontend, Spring Boot API and worker, PostgreSQL, Caddy. Non-root containers. Encrypted backups and isolated restore.

## Tech stack

- Next.js 16.4 with React 19 and TypeScript
- Spring Boot modular monolith on Java 21
- PostgreSQL 16 with Flyway migrations
- Auth0 (production) with a local dev-login fallback
- Playwright, Vitest, JUnit, Testcontainers
- Sigma, Cytoscape, and React Flow for the optional map
- Docker Compose for local and production deployment

## Run it locally

You need four things installed: **Node.js 20.9+**, **Java 21**, **Maven 3.9+** and **Docker Desktop** (running). The app uses three processes: PostgreSQL in Docker, the Spring Boot backend, and the Next.js frontend. Use two terminals.

### 1. Get the code and the settings

```sh
git clone https://github.com/oliverberggren/skill-stack.git
cd skill-stack
cp .env.example .env.local
```

`.env.local` holds the frontend settings. The defaults work as they are.

### 2. Add your OpenAI API key (optional)

Skip this step to run without AI. Every recommendation still works, using the built-in deterministic ranking.

```sh
cp backend/.env.example backend/.env
```

Open `backend/.env` and set:

```
AI_ENABLED=true
AI_PROVIDER=openai
AI_MONTHLY_BUDGET_USD=2
AI_TOTAL_BUDGET_USD=5
OPENAI_API_KEY_SKILL_STACK=sk-your-key-here
```

The budgets are required: at 0, every AI call is blocked. One analysis costs roughly USD 0.007. `backend/.env` is gitignored, and the key is only ever read by the backend, never sent to the browser. Create a key at platform.openai.com, under API keys.

### 3. Start the database

```sh
docker compose up -d postgres
```

### 4. Start the backend (terminal 1)

```sh
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Wait for `Started SkillStackApplication`. The first run downloads dependencies and creates the database tables, so it takes a few minutes. The backend runs on `http://localhost:8080`.

### 5. Start the frontend (terminal 2, from the repo root)

```sh
npm install
npm run dev
```

### 6. Open the app

Go to `http://localhost:3000` and sign in by typing a name. Local sign-in uses a signed development token, and all your data stays in your own PostgreSQL.

### Stopping and resetting

- Stop the backend and frontend with `Ctrl+C` in each terminal.
- `docker compose stop` stops the database and keeps your data.
- `docker compose down -v` deletes the database and all local data.

### If something goes wrong

- **"The Skill Stack API is not reachable"**: the backend is not running, or is still starting. Check terminal 1.
- **Port 5432 is already in use**: another PostgreSQL is running. Stop it, or change the port in `docker-compose.yml`.
- **AI does nothing**: check that `backend/.env` has `AI_ENABLED=true`, `AI_PROVIDER=openai`, budgets above 0 and a key, then restart the backend.
- **After changing `.env.local` or `next.config.ts`**: restart `npm run dev`.

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for self-hosting on a VPS.

## The idea

Read [docs/SKILL-STACK-THESIS.md](docs/SKILL-STACK-THESIS.md) for the thinking behind Skill Stack.

## Tests

```sh
npm test            # Vitest unit tests
npm run test:e2e    # Playwright browser suite
cd backend && mvn test   # Spring Boot test suite with PostgreSQL Testcontainers
```

## Status

This is a personal project. Issues may not get responses. Forks and reuse are welcome under the MIT license.

## License

[MIT](LICENSE).
