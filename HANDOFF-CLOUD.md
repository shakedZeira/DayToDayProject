# DayToDay — Cloud Handoff (2026-11-17)

Continue this work from a cloud Claude session. **This repo is public: never write secrets into it.**

## Part 1 — Paste into the cloud project's instructions

```
Project: DayToDayProject — private personal daily-companion app (single user).
Repo: github.com/shakedZeira/DayToDayProject (public repo, branch main).

Stack
- Monorepo (npm workspaces): client (React 18 + Vite PWA, Tailwind), server (Express 4 +
  Prisma 5 + PostgreSQL, TypeScript, vitest + supertest), shared (types).
- Android app in composeApp/ (Kotlin Compose Multiplatform, Ktor, Room, Koin). Gradle; cannot
  be built in the cloud session unless the Android SDK is available.
- Node 24 LTS (see .nvmrc). Postgres 16 is needed for server tests.

Hosting (all free tier)
- DB: Neon Postgres. API: Render free web service "dtd-api" (Docker, server/Dockerfile,
  autoDeploy from main). Client: Cloudflare Pages, proxying /api/* via client/public/_redirects.
- Render sleeps after ~15 min idle; cron-job.org pings /api/health every 10 min.

Rules
- NEVER commit secrets. Secrets live only in Render env vars, the Neon dashboard and the
  user's local gitignored server/.env.neon. Do not echo them in output.
- Private app: no rate limiting needed. Registration is closed in production after the first user
  (ALLOW_REGISTRATION=true reopens). JWT_SECRET is required in production.
- Prisma migrations: always add a hand-checked migration for schema changes
  (`prisma migrate diff` or `migrate dev`); production runs `prisma migrate deploy` on start.
- server/src/shared.d.ts is a generated copy of shared/src/index.ts:
  run `npm run sync:shared --workspace server` after editing shared types.
- Before finishing a change: `npm run typecheck` and `npm test --workspace server`.
- Commit in small logical commits; do not push to main without the user's go-ahead.
- Recipe photos are stored in Postgres (RecipePhoto table), not on disk. No disk storage anywhere.
- The PDF/Study feature was deleted on purpose; do not reintroduce it.

Environment variables (names only)
DATABASE_URL, JWT_SECRET, NODE_ENV, CLIENT_URL, ALLOW_REGISTRATION, LLM_PROVIDER, LLM_HTTP_URL,
LLM_HTTP_KEY (Groq), LLM_MODEL, VISION_PROVIDER (stub|gemini), GEMINI_API_KEY, VISION_MODEL
(default gemini-2.5-flash), HOST, PORT.
```

## Part 2 — Prerequisites for a cloud session

1. Give the cloud session access to the GitHub repo (it is public; push needs the user's grant).
2. Node 24 and npm: `npm ci` at the repo root installs all workspaces.
3. A Postgres for tests. Options: install postgres in the sandbox, or provide an empty scratch
   database URL. Tests use a database named `dtd_test` and run `prisma db push --force-reset`
   (see `server/vitest.globalSetup.ts`) — **never point this at the Neon production DB.**
   Local default: `postgresql://dtd:dtd@localhost:5432/dtd` (docker-compose.yml / CI service).
4. Optional secrets as cloud-session env vars (the user adds them, they are not in the repo):
   `RENDER_API_KEY` (to inspect/redeploy the service), a Neon connection string only if a task
   explicitly needs production DB access.

## Part 3 — Current state

**Done and pushed to `main`**
- Cooking tab (recipes, favorites, shopping list, cooking mode) on web and Android.
- Recipe photos stored in Postgres. PDF/Study/flashcards/quiz removed everywhere.
- `GET /api/export` JSON backup + "Export my data" button in Settings.
- Auth hardening (JWT_SECRET required in prod, registration closes after first user).
- Deploy consolidated to Neon + Render + Cloudflare Pages; GitHub Actions CI added.
- Migration history fixed: `20261110000000_add_recipes` was missing (Recipe/RecipeFavorite had
  never been in a migration). Neon was baselined with `migrate resolve` and is in sync with the
  schema (verified: `migrate diff` is empty).

**Infrastructure**
- Neon DB created, migrated and seeded. It had no users when handed off.
- Render service `dtd-api` (id `srv-dbu12b3l550s739k6gs0`) created at
  `https://dtd-api-dsqk.onrender.com`, health check `/api/health`. First deploy was started from
  the API; check its status in the Render dashboard before assuming it is live.
- `client/public/_redirects` points at that Render URL.

**Not done / untested**
- The server test suite has not been run since the PDF removal, export, recipe-photo and
  auth changes (Docker/Postgres was unavailable). Run it first.
- `server/Dockerfile` (server-only image) has not been verified beyond the Render build.
- Cloudflare Pages project not created yet. After creating it, set `CLIENT_URL` in Render to the
  Pages URL.
- cron-job.org keep-alive not created.
- First account not registered yet (do this, then registration closes itself).
- The Android app's server URL must be set to the Pages URL in Settings.
- The published roadmap Artifact still lists a wrong item ("remove Android PDF screen" — there is
  no such screen). `docs/ROADMAP.md` is corrected.
- `Projects/` at repo root: 9 Room entity files not referenced by composeApp; verify with a
  Gradle build before deleting.

## Part 4 — Next work (from docs/ROADMAP.md)

1. Run and fix the server tests; make CI green.
2. Log a recipe as a meal (recipes currently do not count toward calories).
3. Server-side shopping list (currently computed in the browser and lost on reload).
4. Offline write queue in the PWA (service worker only caches GET); outbox sync on Android.
5. Weekly review digest, smarter notifications (quiet hours, snooze).
6. Web/Android feature parity audit, accessibility pass, add client and Android tests.

## Part 5 — Security follow-ups for the user

- The Neon password and the Render API key were pasted into a chat session. Rotate both
  (Neon: reset the role password and update `DATABASE_URL` in Render; Render: revoke the API key
  and create a new one) once setup is finished.
- The Spotify client ID is in git history of this public repo (client IDs are not secret, but
  consider whether you want the repo public).
