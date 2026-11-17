# DayToDayProject

A personal all-in-one daily companion — tasks and clock alerts, health tracking, an Italian tutor, and a study/PDF hub. Built as a React + Vite PWA front-end backed by an Express API with Prisma and PostgreSQL. The Workout module includes routines, a shared exercise library, active sessions with a rest timer, and progress/PR analytics (Epley e1RM). A **Today digest** dashboard summarizes your focus task, workout reminder, Italian lesson, and study queue, with a cross-module **Progress** dashboard covering task streaks, weekly completion, workouts, calories, and study stats.

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

## Deploy (free: Neon + Render + Cloudflare Pages)

Architecture: **Neon** (Postgres) <- **Render** (Express API, `server/Dockerfile`, `render.yaml`) <- **Cloudflare Pages** (static PWA). Pages proxies `/api/*` to Render via `client/public/_redirects`, so the browser stays same-origin (no CORS, no client changes). No credit card is needed for any of the three. Do not use Render's own free Postgres (it expires after 30 days).

1. **Database (Neon).** Create a free project at neon.tech and copy the *pooled* connection string (keep `?sslmode=require`).
2. **API (Render).** In the Render dashboard choose New -> Blueprint, pick this GitHub repo; it reads `render.yaml` and creates the free `dtd-api` web service. When prompted, set `DATABASE_URL` (the Neon URL) and optionally `LLM_HTTP_KEY` (Groq key). `JWT_SECRET` is generated for you (the server refuses to start in production without it). The container runs `prisma migrate deploy` and the seed on every start and listens on Render's `PORT`. Note your URL: `https://dtd-api.onrender.com` (or whatever Render assigns).
3. **Client (Cloudflare Pages).** Connect the repo, build command `npm ci && npm run build --workspace client`, output directory `client/dist`. Edit `client/public/_redirects` and the Render host is already set to `dtd-api-dsqk.onrender.com`; change it if you rename the service. Update `CLIENT_URL` in the Render env to your Pages URL.
4. **First account.** Open the Pages URL and register. In production only the first account can be created; afterwards registration is closed (set `ALLOW_REGISTRATION=true` to reopen it temporarily).
5. **Keep-alive.** Render's free tier sleeps after ~15 minutes idle, and a cold start takes about 50 seconds. Create a free job at cron-job.org that GETs `https://<your-app>.onrender.com/api/health` every 10 minutes; this also keeps `node-cron` reminders and web-push firing. (Render free allows ~750 instance-hours/month, enough for one always-on service.)
6. **Android app.** In Settings -> Server enter your Render URL (`https://<your-app>.onrender.com/`).

Note: recipe photos are stored in Postgres (3 MB cap each), so they survive redeploys.

CI (`.github/workflows/ci.yml`) runs typecheck, server/client tests (with a Postgres service) and the client build on every push.

## PWA & offline notes

- The app precaches its shell and serves it offline via `vite-plugin-pwa` (workbox).
- `/api` GET requests are cached network-first (`api-cache-v1`, 1h TTL); POST/PUT/DELETE are never cached.
- Install from the address bar / Add to Home Screen. Icons: `client/public/pwa-192x192.png`, `pwa-512x512.png` (regenerate with `npm run icons --workspace client`).
- iOS Safari: push notifications only work for installed home-screen PWAs and may stop after ~7 days of no use (spec §7). Background push on iOS is a known limitation, not a bug to fix.

## Running the Android app against the server

The Compose app (`composeApp/`) talks to the same Express API on port 4000. Start it first:

```bash
npm run dev --workspace server
# or
cd server && npm run dev
```

The app's **Settings → Server → API base URL** field controls where requests go. The
default is detected at runtime:

- **Android emulator:** `http://10.0.2.2:4000/` (`10.0.2.2` is the host machine as
  seen from the emulator).
- **Physical device:** `http://127.0.0.1:4000/`. If the phone runs the server itself
  (Termux), this already works. To use the server on your PC, either:
  - enter your PC's LAN IP in Settings, e.g. `http://192.168.1.20:4000/` (phone and
    PC on the same Wi-Fi; allow port 4000 through the PC firewall), or
  - connect the phone by USB and run
    ```bash
    adb reverse tcp:4000 tcp:4000
    ```
    then keep the default `http://127.0.0.1:4000/`.

The URL must start with `http://` or `https://`; a trailing slash is optional and is
added automatically. If the app cannot connect it shows the URL it tried and points
back to this setting.

## Project Layout

```
client/   — React + Vite PWA (TypeScript, Tailwind CSS)
server/   — Express API (TypeScript, Prisma, PostgreSQL)
shared/   — Types and utilities shared between client and server
```
