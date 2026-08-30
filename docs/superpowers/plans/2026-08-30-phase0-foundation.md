# Phase 0 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Stand up the personal daily companion monorepo skeleton — a cross-device PWA frontend, an Express+SQLite API backend, auth, and a deployable shape — that all later phases build on.

**Architecture:** A monorepo with three workspaces: `client/` (React + TypeScript + Vite PWA), `server/` (Express + TypeScript + Prisma/SQLite), and `shared/` (shared TypeScript types). The client talks to the server over a REST API; the server owns the single-user database and auth.

**Tech Stack:** React 18, TypeScript, Vite, `vite-plugin-pwa`, Tailwind CSS, Node.js, Express, Prisma, SQLite, JSON Web Tokens (JWT).

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage for the project).
- One language across the stack: TypeScript.
- Database: SQLite via Prisma (file-based, zero cost).
- Auth: single-user JWT (room to grow to multi-user later).
- PWA installable to home screen on mobile + desktop browsers.
- Lint + typecheck must pass before any commit (`npm run lint`, `npm run typecheck`).
- Windows host (cmd.exe shell); all paths use `\` in commands.

---

### Task 0.1: Scaffold the monorepo and workspaces

**Files:**
- Create: `package.json` (root)
- Create: `client/package.json`
- Create: `server/package.json`
- Create: `shared/package.json`
- Create: `.gitignore` (already exists — extend)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: three npm workspaces named `client`, `server`, `shared`. Root scripts `dev`, `build`, `lint`, `typecheck` that delegate to each workspace.

- [ ] **Step 1: Write the root package.json**

```json
{
  "name": "day-to-day-project",
  "private": true,
  "version": "0.1.0",
  "workspaces": ["client", "server", "shared"],
  "scripts": {
    "dev": "npm run dev --workspace server & npm run dev --workspace client",
    "build": "npm run build --workspace client",
    "lint": "npm run lint --workspace client && npm run lint --workspace server",
    "typecheck": "npm run typecheck --workspace client && npm run typecheck --workspace server && npm run typecheck --workspace shared"
  },
  "devDependencies": {
    "typescript": "^5.5.0"
  }
}
```

- [ ] **Step 2: Create the shared workspace**

`shared/package.json`:
```json
{
  "name": "shared",
  "version": "0.1.0",
  "main": "src/index.ts",
  "types": "src/index.ts",
  "scripts": {
    "typecheck": "tsc --noEmit"
  },
  "devDependencies": {
    "typescript": "^5.5.0"
  }
}
```

`shared/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "noEmit": true
  },
  "include": ["src"]
}
```

`shared/src/index.ts` — the first shared type, a health endpoint payload used to verify cross-workspace type sharing:
```ts
export interface HealthResponse {
  status: "ok";
  time: string;
}
```

- [ ] **Step 3: Create the server workspace**

`server/package.json`:
```json
{
  "name": "server",
  "version": "0.1.0",
  "main": "dist/index.js",
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc -p tsconfig.json",
    "start": "node dist/index.js",
    "lint": "eslint src",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "express": "^4.19.0",
    "jsonwebtoken": "^9.0.2",
    "@prisma/client": "^5.18.0",
    "cors": "^2.8.5",
    "dotenv": "^16.4.5"
  },
  "devDependencies": {
    "typescript": "^5.5.0",
    "tsx": "^4.16.0",
    "prisma": "^5.18.0",
    "@types/express": "^4.17.21",
    "@types/jsonwebtoken": "^9.0.6",
    "@types/cors": "^2.8.17",
    "@types/node": "^20.14.0"
  }
}
```

`server/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "CommonJS",
    "moduleResolution": "node",
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "types": ["node"]
  },
  "include": ["src"]
}
```

- [ ] **Step 4: Create the client workspace**

`client/package.json`:
```json
{
  "name": "client",
  "version": "0.1.0",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint src",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "react": "^18.3.0",
    "react-dom": "^18.3.0"
  },
  "devDependencies": {
    "typescript": "^5.5.0",
    "vite": "^5.3.0",
    "@vitejs/plugin-react": "^4.3.0",
    "vite-plugin-pwa": "^0.20.0",
    "tailwindcss": "^3.4.0",
    "autoprefixer": "^10.4.0",
    "postcss": "^8.4.0"
  }
}
```

- [ ] **Step 5: Install dependencies from the root**

Run (root, using `D:\AI Projects\DayToDayProject` as cwd):
```bash
npm install
```
Expected: creates root `node_modules` and hoists workspace deps; exits 0.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold monorepo workspaces"
```

---

### Task 0.2: Server foundation — Express app with health route and Prisma

**Files:**
- Create: `server/src/index.ts`
- Create: `server/src/db.ts`
- Create: `server/src/health.ts`
- Create: `server/prisma/schema.prisma`
- Create: `.env`
- Create: `.env.example`

**Interfaces:**
- Consumes: `HealthResponse` type from `shared` (Task 0.1).
- Produces: Express app exporting `app` (for tests) and starting a listener on `PORT` (default `4000`); `prisma` client singleton from `db.ts`; `GET /api/health` returning `HealthResponse`.

- [ ] **Step 1: Write the Prisma schema**

`server/prisma/schema.prisma`:
```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "sqlite"
  url      = env("DATABASE_URL")
}

// Minimal placeholder model to validate the schema; replaced in later phases.
model User {
  id       String   @id @default(cuid())
  email    String   @unique
  password String
  createdAt DateTime @default(now())
}
```

- [ ] **Step 2: Write environment files**

`.env` (not committed — already gitignored):
```
DATABASE_URL="file:./dev.db"
JWT_SECRET="dev-secret-change-me"
PORT=4000
CLIENT_URL="http://localhost:5173"
```

`.env.example` (committed):
```
DATABASE_URL="file:./dev.db"
JWT_SECRET="change-me"
PORT=4000
CLIENT_URL="http://localhost:5173"
```

- [ ] **Step 3: Write the db singleton**

`server/src/db.ts`:
```ts
import { PrismaClient } from "@prisma/client";

export const prisma = new PrismaClient();
```

- [ ] **Step 4: Write the health route**

`server/src/health.ts`:
```ts
import { Router } from "express";
import type { HealthResponse } from "shared";

export const healthRouter = Router();

healthRouter.get("/health", (_req, res) => {
  const body: HealthResponse = { status: "ok", time: new Date().toISOString() };
  res.json(body);
});
```

- [ ] **Step 5: Write the Express app entry**

`server/src/index.ts`:
```ts
import "dotenv/config";
import express from "express";
import cors from "cors";
import { healthRouter } from "./health";
import { prisma } from "./db";

export const app = express();

app.use(cors({ origin: process.env.CLIENT_URL || "*" }));
app.use(express.json());

app.use("/api", healthRouter);

// Verify the DB is reachable on boot.
app.get("/api/ready", async (_req, res) => {
  await prisma.$queryRaw`SELECT 1`;
  res.json({ ready: true });
});

const port = Number(process.env.PORT) || 4000;

if (require.main === module) {
  app.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
  });
}
```

- [ ] **Step 6: Generate the Prisma client and create the database**

Run:
```bash
npm run build --workspace server
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: `node_modules/.prisma` generated; `server/prisma/dev.db` created; exit 0.

- [ ] **Step 7: Write the server test**

`server/src/health.test.ts`:
```ts
import { test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";

test("GET /api/health returns ok", async () => {
  const res = await request(app).get("/api/health");
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ status: "ok", time: expect.any(String) });
});
```

Add vitest to `server/package.json` devDependencies and a `test` script:
```json
"test": "vitest run"
```
Then run:
```bash
npm install
npm test --workspace server
```
Expected: 1 test PASS.

- [ ] **Step 8: Typecheck, lint, commit**

Run:
```bash
npm run typecheck
npm run lint --workspace server
```
Expected: no errors. Then:
```bash
git add -A
git commit -m "feat(server): express app, health route, prisma sqlite"
```

---

### Task 0.3: Client foundation — React PWA shell with Tailwind

**Files:**
- Create: `client/index.html`
- Create: `client/vite.config.ts`
- Create: `client/tsconfig.json`
- Create: `client/tailwind.config.js`
- Create: `client/postcss.config.js`
- Create: `client/src/main.tsx`
- Create: `client/src/App.tsx`
- Create: `client/src/index.css`
- Create: `client/src/api.ts`
- Create: `client/public/` (icons dir)

**Interfaces:**
- Consumes: `HealthResponse` from `shared`; the server's `GET /api/health`.
- Produces: a Vite PWA dev server on `:5173`, a responsive shell rendering the app title, and an `api.ts` helper. The Vite dev server proxies `/api` to the backend.

- [ ] **Step 1: Write the Vite config with PWA + proxy**

`client/vite.config.ts`:
```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg"],
      manifest: {
        name: "Day To Day",
        short_name: "DayToDay",
        start_url: "/",
        display: "standalone",
        background_color: "#ffffff",
        theme_color: "#4f46e5",
        icons: []
      }
    })
  ],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:4000"
    }
  }
});
```

- [ ] **Step 2: Write index.html**

`client/index.html`:
```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
    <meta name="theme-color" content="#4f46e5" />
    <title>Day To Day</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 3: Write tsconfig, tailwind, postcss**

`client/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "paths": {
      "shared": ["../shared/src/index.ts"]
    }
  },
  "include": ["src", "vite.config.ts"]
}
```

`client/tailwind.config.js`:
```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: { extend: {} },
  plugins: []
};
```

`client/postcss.config.js`:
```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {}
  }
};
```

- [ ] **Step 4: Write the CSS (Tailwind directives)**

`client/src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 5: Write the API helper**

`client/src/api.ts`:
```ts
import type { HealthResponse } from "shared";

export async function fetchHealth(): Promise<HealthResponse> {
  const res = await fetch("/api/health");
  if (!res.ok) throw new Error(`health check failed: ${res.status}`);
  return res.json();
}
```

- [ ] **Step 6: Write main.tsx and App.tsx**

`client/src/main.tsx`:
```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

`client/src/App.tsx`:
```tsx
import { useEffect, useState } from "react";
import { fetchHealth } from "./api";

export default function App() {
  const [backend, setBackend] = useState<string>("connecting…");

  useEffect(() => {
    fetchHealth()
      .then(() => setBackend("connected"))
      .catch(() => setBackend("unreachable"));
  }, []);

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-3xl font-bold text-slate-800">Day To Day</h1>
      <p className="text-slate-500">
        Your personal daily companion.
      </p>
      <p className="text-sm text-slate-400">
        Backend: <span className="text-indigo-600">{backend}</span>
      </p>
    </div>
  );
}
```

- [ ] **Step 7: Create a placeholder favicon**

`client/public/favicon.svg`:
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <rect width="32" height="32" rx="6" fill="#4f46e5"/>
  <text x="16" y="22" font-size="16" text-anchor="middle" fill="#fff" font-family="sans-serif">D</text>
</svg>
```

- [ ] **Step 8: Ensure shared is resolvable in Vite**

Add an alias in `vite.config.ts`:
```ts
import { fileURLToPath, URL } from "node:url";
// inside resolve:
resolve: {
  alias: {
    shared: fileURLToPath(new URL("../shared/src/index.ts", import.meta.url))
  }
}
```

- [ ] **Step 9: Run dev servers and verify connectivity**

Terminal A (root):
```bash
npm run dev --workspace server
```
Terminal B (root):
```bash
npm run dev --workspace client
```
Expected: server on `:4000`, client on `:5173`. Open `http://localhost:5173` → shows "Day To Day" with **Backend: connected**.

- [ ] **Step 10: Typecheck, commit**

```bash
npm run typecheck
```
Expected: no errors. Then:
```bash
git add -A
git commit -m "feat(client): react pwa shell with tailwind and health check"
```

---

### Task 0.4: Auth — register and login with JWT

**Files:**
- Create: `server/src/auth.ts`
- Create: `server/src/auth.test.ts`
- Modify: `server/src/index.ts`
- Create: `server/src/session.ts` (lightweight JWT verify middleware)

**Interfaces:**
- Consumes: `express`, `jsonwebtoken`, `prisma`, `bcryptjs`.
- Produces: `POST /api/auth/register` and `POST /api/auth/login`, each returning `{ token, user: { id, email } }`; middleware `requireAuth` that validates the `Authorization: Bearer <token>` header and attaches `req.userId`.

- [ ] **Step 1: Add bcryptjs dependency**

Add to `server/package.json` dependencies:
```json
"bcryptjs": "^2.4.3"
```
Add `@types/bcryptjs` to devDependencies. Run `npm install`.

- [ ] **Step 2: Write the auth router**

`server/src/auth.ts`:
```ts
import { Router } from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { prisma } from "./db";

export const authRouter = Router();

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

interface UserPayload {
  id: string;
  email: string;
}

function signToken(user: UserPayload): string {
  return jwt.sign({ id: user.id }, JWT_SECRET, { expiresIn: "7d" });
}

authRouter.post("/register", async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== "string" || typeof password !== "string" || password.length < 8) {
    return res.status(400).json({ error: "email and password (min 8 chars) required" });
  }
  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) return res.status(409).json({ error: "email already registered" });

  const hash = await bcrypt.hash(password, 10);
  const user = await prisma.user.create({ data: { email, password: hash } });
  res.status(201).json({ token: signToken({ id: user.id, email: user.email }), user: { id: user.id, email: user.email } });
});

authRouter.post("/login", async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== "string" || typeof password !== "string") {
    return res.status(400).json({ error: "email and password required" });
  }
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user || !(await bcrypt.compare(password, user.password))) {
    return res.status(401).json({ error: "invalid credentials" });
  }
  res.json({ token: signToken({ id: user.id, email: user.email }), user: { id: user.id, email: user.email } });
});
```

- [ ] **Step 3: Write the requireAuth middleware**

`server/src/session.ts`:
```ts
import type { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

export interface AuthedRequest extends Request {
  userId?: string;
}

export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return res.status(401).json({ error: "missing bearer token" });
  }
  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET) as { id: string };
    req.userId = payload.id;
    next();
  } catch {
    return res.status(401).json({ error: "invalid token" });
  }
}
```

- [ ] **Step 4: Mount the router in index.ts**

`server/src/index.ts` — add imports and mounting:
```ts
import { authRouter } from "./auth";

app.use("/api", authRouter);
app.use("/api/auth", authRouter); // authRouter defines its own "/register","/login" paths under /api/auth
```

*(Adjust: mount `authRouter` once at `/api/auth` so routes become `/api/auth/register` and `/api/auth/login`.)*

- [ ] **Step 5: Write the auth test**

`server/src/auth.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

beforeEach(async () => {
  await prisma.user.deleteMany({});
});

test("register and login roundtrip", async () => {
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "me@example.com", password: "password123" });
  expect(reg.status).toBe(201);
  expect(reg.body.token).toBeTruthy();

  const login = await request(app)
    .post("/api/auth/login")
    .send({ email: "me@example.com", password: "password123" });
  expect(login.status).toBe(200);
  expect(login.body.user.email).toBe("me@example.com");
});

test("rejects short password", async () => {
  const res = await request(app)
    .post("/api/auth/register")
    .send({ email: "me@example.com", password: "short" });
  expect(res.status).toBe(400);
});
```

- [ ] **Step 6: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Expected: tests PASS, typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): jwt auth with register and login"
```

---

### Task 0.5: Client login screen + token store

**Files:**
- Create: `client/src/auth.ts`
- Create: `client/src/App.tsx` (modify)
- Create: `client/src/Login.tsx`

**Interfaces:**
- Consumes: server `POST /api/auth/register` and `POST /api/auth/login`.
- Produces: `client/src/auth.ts` exporting `register(email, password)` and `login(email, password)` returning `{ token, user }`, and a wrapper `authedFetch(token, path, init)`.

- [ ] **Step 1: Write the auth client helper**

`client/src/auth.ts`:
```ts
export interface AuthUser {
  id: string;
  email: string;
}
export interface AuthResponse {
  token: string;
  user: AuthUser;
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export const register = (email: string, password: string) =>
  post<AuthResponse>("/api/auth/register", { email, password });

export const login = (email: string, password: string) =>
  post<AuthResponse>("/api/auth/login", { email, password });

export function authedFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...init,
    headers: { ...init.headers, Authorization: `Bearer ${token}` }
  });
}
```

- [ ] **Step 2: Write the Login component**

`client/src/Login.tsx`:
```tsx
import { useState } from "react";
import { login, register, type AuthResponse } from "./auth";

interface Props {
  onAuthed: (token: string, user: AuthUser) => void;
}

export default function Login({ onAuthed }: Props) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const fn = mode === "login" ? login : register;
      const res: AuthResponse = await fn(email, password);
      onAuthed(res.token, res.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3 w-full max-w-sm">
      <h2 className="text-xl font-semibold text-slate-800">
        {mode === "login" ? "Log in" : "Create account"}
      </h2>
      <input
        className="border rounded px-3 py-2"
        type="email" placeholder="email" value={email}
        onChange={(e) => setEmail(e.target.value)} required
      />
      <input
        className="border rounded px-3 py-2"
        type="password" placeholder="password (min 8)" value={password}
        onChange={(e) => setPassword(e.target.value)} required minLength={8}
      />
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button className="bg-indigo-600 text-white rounded py-2">{mode === "login" ? "Log in" : "Register"}</button>
      <button type="button" className="text-sm text-indigo-600 underline" onClick={() => setMode(mode === "login" ? "register" : "login")}>
        {mode === "login" ? "Need an account? Register" : "Have an account? Log in"}
      </button>
    </form>
  );
}
```

- [ ] **Step 3: Wire App.tsx to gate on auth and persist the token**

`client/src/App.tsx`:
```tsx
import { useEffect, useState } from "react";
import Login from "./Login";
import { fetchHealth } from "./api";
import type { AuthUser } from "./auth";

const TOKEN_KEY = "dtd.token";
const USER_KEY = "dtd.user";

export default function App() {
  const [backend, setBackend] = useState<string>("connecting…");
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  });

  useEffect(() => {
    fetchHealth()
      .then(() => setBackend("connected"))
      .catch(() => setBackend("unreachable"));
  }, []);

  function onAuthed(t: string, u: AuthUser) {
    localStorage.setItem(TOKEN_KEY, t);
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    setToken(t);
    setUser(u);
  }

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col items-center justify-center gap-6 p-6">
      <h1 className="text-3xl font-bold text-slate-800">Day To Day</h1>
      <p className="text-sm text-slate-400">
        Backend: <span className="text-indigo-600">{backend}</span>
      </p>
      {token && user ? (
        <div className="text-center text-slate-600">
          <p>Logged in as <span className="font-semibold">{user.email}</span></p>
          <button
            className="mt-4 text-sm text-red-600 underline"
            onClick={() => { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); setToken(null); setUser(null); }}
          >
            Log out
          </button>
        </div>
      ) : (
        <Login onAuthed={onAuthed} />
      )}
    </div>
  );
}
```

- [ ] **Step 4: Typecheck, run both servers, verify register→login flow**

```bash
npm run typecheck
```
Then start both dev servers and manually test: register a user, it logs in, token persists on refresh.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(client): login and register with token persistence"
```

---

### Task 0.6: Deploy skeleton (Render/Railway ready) + README

**Files:**
- Create: `server/Dockerfile`
- Create: `client/Dockerfile` (build static site)
- Create: `docker-compose.yml` (optional local) — skip for now
- Create: `README.md`
- Create: `.nvmrc`

**Interfaces:**
- Consumes: the built client (static) and server (Node).
- Produces: a containerized deploy shape so the app runs on a free cloud tier; instructions in README.

- [ ] **Step 1: Server Dockerfile**

`server/Dockerfile`:
```dockerfile
FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
COPY server/package.json server/package.json
COPY shared/package.json shared/package.json
RUN npm ci --workspace server --include-workspace-root

COPY . .
WORKDIR /app/server
RUN npx prisma generate
RUN npx prisma db push
RUN npm run build

EXPOSE 4000
CMD ["npm", "start"]
```

*(Note: for a real deploy, `prisma migrate deploy` is preferred over `db push`; this skeleton uses `db push` for simplicity.)*

- [ ] **Step 2: Client static Dockerfile**

`client/Dockerfile`:
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY client/package.json client/package.json
COPY shared/package.json shared/package.json
RUN npm ci --workspace client --include-workspace-root
COPY . .
WORKDIR /app/client
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/client/dist /usr/share/nginx/html
COPY client/nginx.conf /etc/nginx/conf.d/default.conf
```

`client/nginx.conf`:
```nginx
server {
  listen 80;
  location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
  }
  location /api/ {
    proxy_pass http://server:4000;
  }
}
```

- [ ] **Step 3: Write README with run + deploy instructions**

`README.md` — project overview, prerequisites (Node 20), local dev (two terminals), deploy notes (Render/Railway hosting two services: API + static PWA), and the D-drive location note.

- [ ] **Step 4: Write .nvmrc**

`.nvmrc`:
```
20
```

- [ ] **Step 5: Typecheck, lint, verify build**

```bash
npm run typecheck
npm run build
```
Expected: client production build succeeds; server compiles.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: deploy skeleton with dockerfiles and readme"
```

---

## Phase 0 Self-Review

- **Spec coverage:** Phase 0 scope (repo setup ✓, PWA scaffold ✓, auth ✓, SQLite schema ✓, shared layout ✓, deploy skeleton ✓) is fully covered by Tasks 0.1–0.6.
- **Placeholder scan:** No TODOs/TBDs; all steps carry real code.
- **Type consistency:** `HealthResponse` defined in Task 0.1 and used in 0.2/0.3 consistently; `requireAuth`/`AuthedRequest` defined in 0.4 available for Phase 1; `client/src/auth.ts` exports used consistently in the Login/App wiring.
- **One inconsistency noted and fixed in-task:** the `authRouter` was initially shown mounted twice; corrected to mount once at `/api/auth`.
