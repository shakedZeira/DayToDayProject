# DayToDayProject

A personal all-in-one daily companion — tasks and clock alerts, health tracking, an Italian tutor, and a study/PDF hub. Built as a React + Vite PWA front-end backed by an Express API with Prisma and SQLite.

## Prerequisites

- **Node 24 LTS** (pinned in `.nvmrc`). Local development requires Node 20+; the deploy skeleton and Dockerfiles target Node 24 for consistency. (Node 18 can run dev servers but the client production build needs Node 20+ due to `vite-plugin-pwa`.)
- npm (comes with Node)

## Local Development

From the repo root (`D:\AI Projects\DayToDayProject`), open two terminals:

**Terminal A — API (port 4000)**

```bash
npm run dev --workspace server
```

**Terminal B — Client (port 5173)**

```bash
npm run dev --workspace client
```

Open <http://localhost:5173>.

### Environment Variables

Copy the example file and set a real secret:

```bash
cp .env.example .env
# Edit .env and replace JWT_SECRET with a real value
```

## Deploy (Render / Railway)

Two services, both runnable on a free tier:

| Service | Directory | Build file | Port |
|---------|-----------|------------|------|
| **API** | `server/` | `server/Dockerfile` | 4000 |
| **Static PWA** | `client/` | `client/Dockerfile` | 80 (nginx) |

The client's nginx config proxies `/api/` to `http://server:4000`. On most platforms this assumes an internal hostname `server`; you may need to set or override this via an environment variable or platform service URL depending on your hosting setup.

## Project Layout

```
client/   — React + Vite PWA (TypeScript, Tailwind CSS)
server/   — Express API (TypeScript, Prisma, SQLite)
shared/   — Types and utilities shared between client and server
```
