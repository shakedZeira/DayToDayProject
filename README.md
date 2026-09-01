# DayToDayProject

A personal all-in-one daily companion — tasks and clock alerts, health tracking, an Italian tutor, and a study/PDF hub. Built as a React + Vite PWA front-end backed by an Express API with Prisma and PostgreSQL. The Workout module includes routines, a shared exercise library, active sessions with a rest timer, and progress/PR analytics (Epley e1RM).

## Prerequisites

- **Node 24 LTS** (pinned in `.nvmrc`). Local development requires Node 20+; the deploy skeleton and Dockerfiles target Node 24 for consistency. (Node 18 can run dev servers but the client production build needs Node 20+ due to `vite-plugin-pwa`.)
- npm (comes with Node)
- Docker (for local Postgres)

## Local Development

From the repo root (`D:\AI Projects\DayToDayProject`), open two terminals:

**Terminal A — Database**

```bash
docker compose up -d db
# Wait ~5s for health check to pass, then:
npx prisma migrate dev --schema server/prisma/schema.prisma
npm run seed --workspace server
```

**Terminal B — API (port 4000)**

```bash
npm run dev --workspace server
```

**Terminal C — Client (port 5173)**

```bash
npm run dev --workspace client
```

Open <http://localhost:5173>.

### Database Scripts

| Script | Description |
|--------|-------------|
| `npm run db:up --workspace server` | Start local Postgres via Docker |
| `npm run db:migrate --workspace server` | Run Prisma migrations against dev DB |
| `npm run db:deploy --workspace server` | Apply pending migrations (production) |
| `npm run seed --workspace server` | Seed shared catalogs (foods, exercises, default routines) |

### Environment Variables

Copy the example file and set a real secret:

```bash
cp .env.example .env
# Edit .env and replace JWT_SECRET with a real value
```

## Running Tests

Tests use a separate `dtd_test` database on the same local Postgres instance. The test DB schema is auto-synced before each run.

```bash
npm test --workspace server
```

The `vitest.globalSetup.ts` sets `DATABASE_URL` to `dtd_test` and runs `prisma db push` to ensure schema parity. The `vitest.config.ts` `test.env` also points to `dtd_test` as a belt-and-suspenders measure.

## Deploy (Render / Railway)

A `render.yaml` Blueprint deploys three resources:

| Resource | Type | Details |
|----------|------|---------|
| **api** | Web Service | `server/Dockerfile`, runs `prisma migrate deploy` + seed on start |
| **client** | Web Service | `client/Dockerfile` (nginx static PWA) |
| **dtd-db** | PostgreSQL | Render-managed Postgres; `DATABASE_URL` wired to the API service |

The client's nginx config proxies `/api/` to the API service's internal hostname.

## Project Layout

```
client/   — React + Vite PWA (TypeScript, Tailwind CSS)
server/   — Express API (TypeScript, Prisma, PostgreSQL)
shared/   — Types and utilities shared between client and server
```
