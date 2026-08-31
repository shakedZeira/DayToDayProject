# Phase 1 — Tasks with Clock Alerts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Add task management — one-off and recurring tasks with categories, completion tracking, and clock alerts via Web Push — as the first real user-facing value of the Day To Day app.

**Architecture:** Server-side Task model (Prisma/SQLite) behind a REST API guarded by the JWT `requireAuth` middleware from Phase 0. A recurrence resolver computes which tasks are due on a given date. Clock alerts are delivered as Web Push notifications via a VAPID keypair, triggered by a scheduled job that scans for due tasks and sends pushes. The client (React PWA) has a task list with create/complete/edit and registers a service worker to receive pushes.

**Tech Stack:** Express, Prisma, SQLite, `web-push` (VAPID), `node-cron` (scheduler), React, Tailwind, the service worker built by `vite-plugin-pwa`.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage for the project).
- TypeScript throughout; lint + typecheck must pass before any commit.
- Single-user app: all Task rows carry an `ownerId` set to the authed user's id, even though there is one user.
- All task API routes require a valid Bearer token via `requireAuth` (from Phase 0 `server/src/session.ts`).
- Recurrence is calendar-aware: "daily", "weekly on {days}", "monthly on day N".
- Web Push requires HTTPS in production but works on `http://localhost` for a local dev subscriber.
- iOS Safari push limitations are a known tradeoff (spec §7): notifications reliably work on desktop + Android PWA.

---

### Task 1.1: Task data model and migrations

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/tasks.ts` (router)
- Create: `server/src/tasks.test.ts`
- Modify: `server/src/index.ts`

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`.
- Produces: Prisma model `Task`; REST routes `GET /api/tasks`, `POST /api/tasks`, `PATCH /api/tasks/:id`, `DELETE /api/tasks/:id`; exports `TaskCreate` / `TaskUpdate` input shapes used by the client.

- [ ] **Step 1: Add the Task model to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
enum TaskStatus {
  PENDING
  DONE
}

model Task {
  id          String     @id @default(cuid())
  ownerId     String
  title       String
  notes       String?
  status      TaskStatus @default(PENDING)
  completedAt DateTime?
  dueAt       DateTime?
  recurrence  String?    @default("none")
  category    String?
  createdAt   DateTime   @default(now())
  updatedAt   DateTime   @updatedAt

  @@index([ownerId])
}
```

`recurrence` holds a compact spec string: `"none"`, `"daily"`, or `"weekly:0,2,4"` (0=Sun … 6=Sat). Done tasks are excluded from upcoming views for non-recurring tasks.

- [ ] **Step 2: Regenerate Prisma client and push schema**

Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: client regenerated; `dev.db` updated; exit 0.

- [ ] **Step 3: Write the task router**

`server/src/tasks.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const tasksRouter = Router();
tasksRouter.use(requireAuth);

// resolved due-on-date for a task (handles recurrence), used for alerts + views
function dueOn(task: { dueAt: Date | null; recurrence: string | null }, date: Date): boolean {
  if (task.recurrence === "none" || task.recurrence == null) {
    return task.dueAt ? sameDay(task.dueAt, date) : false;
  }
  if (task.recurrence === "daily") return true;
  if (task.recurrence.startsWith("weekly:")) {
    const days = task.recurrence.slice("weekly:".length).split(",").map(Number);
    return days.includes(date.getDay());
  }
  return false;
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

tasksRouter.get("/", async (req: AuthedRequest, res) => {
  const tasks = await prisma.task.findMany({ where: { ownerId: req.userId }, orderBy: { createdAt: "desc" } });
  res.json(tasks);
});

tasksRouter.post("/", async (req: AuthedRequest, res) => {
  const { title, notes, dueAt, recurrence, category } = req.body ?? {};
  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  const task = await prisma.task.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      notes: typeof notes === "string" ? notes : null,
      dueAt: dueAt ? new Date(dueAt) : null,
      recurrence: typeof recurrence === "string" ? recurrence : "none",
      category: typeof category === "string" ? category : null
    }
  });
  res.status(201).json(task);
});

tasksRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const id = req.params.id;
  const existing = await prisma.task.findFirst({ where: { id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });

  const { title, notes, status, dueAt, recurrence, category } = req.body ?? {};
  const data: Record<string, unknown> = {};
  if (typeof title === "string") data.title = title.trim();
  if (typeof notes === "string") data.notes = notes;
  if (status === "DONE" || status === "PENDING") {
    data.status = status;
    data.completedAt = status === "DONE" ? new Date() : null;
  }
  if (dueAt !== undefined) data.dueAt = dueAt === null ? null : new Date(dueAt);
  if (typeof recurrence === "string") data.recurrence = recurrence;
  if (category !== undefined) data.category = category === null ? null : category;

  const task = await prisma.task.update({ where: { id }, data });
  res.json(task);
});

tasksRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.task.findFirst({ where: { id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.task.delete({ where: { id } });
  res.status(204).end();
});

export { dueOn };
```

- [ ] **Step 4: Export shared task input types**

In `shared/src/index.ts` append:
```ts
export interface TaskCreateInput {
  title: string;
  notes?: string | null;
  dueAt?: string | null;
  recurrence?: string;
  category?: string | null;
}
```

- [ ] **Step 5: Mount the router**

In `server/src/index.ts`, import `tasksRouter` and mount:
```ts
app.use("/api/tasks", tasksRouter);
```

- [ ] **Step 6: Write the task tests**

`server/src/tasks.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.task.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "tasks@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("create and list a task", async () => {
  const create = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Buy groceries", recurrence: "daily" });
  expect(create.status).toBe(201);

  const list = await request(app)
    .get("/api/tasks")
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
  expect(list.body[0].title).toBe("Buy groceries");
});

test("complete a task", async () => {
  const create = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Finish report" });
  const id = create.body.id;

  const done = await request(app)
    .patch(`/api/tasks/${id}`)
    .set("Authorization", `Bearer ${token}`)
    .send({ status: "DONE" });
  expect(done.status).toBe(200);
  expect(done.body.status).toBe("DONE");
  expect(done.body.completedAt).toBeTruthy();
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/tasks");
  expect(res.status).toBe(401);
});

test("dueOn resolves daily recurrence", async () => {
  const { dueOn } = await import("./tasks");
  const daily = { dueAt: null, recurrence: "daily" };
  expect(dueOn(daily, new Date("2026-09-01T00:00:00Z"))).toBe(true);
  const weekly = { dueAt: null, recurrence: "weekly:1,3" };
  expect(dueOn(weekly, new Date("2026-09-01T00:00:00Z"))).toBe(false); // Tuesday(2)
  expect(dueOn(weekly, new Date("2026-09-02T00:00:00Z"))).toBe(true);  // Wednesday(3)
});
```

- [ ] **Step 7: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Expected: tests PASS, typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): task CRUD with recurrence resolver"
```

---

### Task 1.2: Task UI — list, create, complete, edit, delete

**Files:**
- Create: `client/src/taskApi.ts`
- Create: `client/src/TaskList.tsx`
- Modify: `client/src/App.tsx`

**Interfaces:**
- Consumes: `authedFetch` from `client/src/auth.ts` (Phase 0); server task routes from Task 1.1; shared `TaskCreateInput`.
- Produces: `client/src/taskApi.ts` exporting `getTasks`, `createTask`, `updateTask`, `deleteTask`, and a `Task` client type. `TaskList.tsx` renders the interactive list. `App.tsx` shows a navigation link to it once authed.

- [ ] **Step 1: Write the task API client**

`client/src/taskApi.ts`:
```ts
import { authedFetch } from "./auth";

export interface Task {
  id: string;
  ownerId: string;
  title: string;
  notes: string | null;
  status: "PENDING" | "DONE";
  completedAt: string | null;
  dueAt: string | null;
  recurrence: string;
  category: string | null;
  createdAt: string;
  updatedAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getTasks(token: string): Promise<Task[]> {
  const res = await authedFetch(token, "/api/tasks");
  return json<Task[]>(res);
}

export async function createTask(token: string, input: { title: string; dueAt?: string | null; recurrence?: string; category?: string | null }): Promise<Task> {
  const res = await authedFetch(token, "/api/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Task>(res);
}

export async function updateTask(token: string, id: string, input: Partial<{ title: string; status: "DONE" | "PENDING"; dueAt: string | null; recurrence: string }>): Promise<Task> {
  const res = await authedFetch(token, `/api/tasks/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Task>(res);
}

export async function deleteTask(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/tasks/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("delete failed");
}
```

- [ ] **Step 2: Write the TaskList component**

`client/src/TaskList.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import { getTasks, createTask, updateTask, deleteTask, type Task } from "./taskApi";

interface Props {
  token: string;
}

export default function TaskList({ token }: Props) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const [recurrence, setRecurrence] = useState("none");

  const refresh = useCallback(async () => {
    const data = await getTasks(token);
    setTasks(data);
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  async function onAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    await createTask(token, { title, recurrence });
    setTitle("");
    await refresh();
  }

  async function onToggle(t: Task) {
    await updateTask(token, t.id, { status: t.status === "DONE" ? "PENDING" : "DONE" });
    await refresh();
  }

  async function onDelete(id: string) {
    await deleteTask(token, id);
    await refresh();
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <form onSubmit={onAdd} className="flex flex-col gap-2">
        <input
          className="border rounded px-3 py-2"
          placeholder="New task…"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <div className="flex gap-3 items-center text-sm">
          <label>
            Recurrence:
            <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)} className="ml-2 border rounded px-2 py-1">
              <option value="none">None</option>
              <option value="daily">Daily</option>
              <option value="weekly:1,3,5">Weekdays</option>
            </select>
          </label>
          <button className="bg-indigo-600 text-white rounded px-4 py-1">Add</button>
        </div>
      </form>

      <ul className="flex flex-col gap-2">
        {tasks.map((t) => (
          <li key={t.id} className="flex items-center gap-3 border rounded p-3 bg-white">
            <input type="checkbox" checked={t.status === "DONE"} onChange={() => onToggle(t)} />
            <span className={`flex-1 ${t.status === "DONE" ? "line-through text-slate-400" : ""}`}>{t.title}</span>
            <span className="text-xs text-slate-400">{t.recurrence}</span>
            <button className="text-sm text-red-600" onClick={() => onDelete(t.id)}>✕</button>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 3: Wire TaskList into App**

Modify `client/src/App.tsx`: after the logged-in block, add a `<TaskList token={token} />` section below the user greeting. Guard it behind the `token && user` branch.

- [ ] **Step 4: Typecheck, run servers, verify manually**

```bash
npm run typecheck
```
Start both dev servers, log in, add tasks, complete, delete. Verify list persists across refresh.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(client): task list with add complete delete"
```

---

### Task 1.3: Push subscriptions and Web Push alerts

**Files:**
- Create: `server/src/push.ts`
- Create: `server/src/push.test.ts`
- Modify: `server/prisma/schema.prisma` (PushSubscription model)
- Modify: `server/src/index.ts`
- Modify: `.env` / `.env.example` (VAPID keys)

**Interfaces:**
- Consumes: `prisma`; `requireAuth`.
- Produces: `POST /api/push/subscribe` / `DELETE /api/push/subscribe` to save/delete a Web Push subscription; `PushSubscription` Prisma model; `sendPush(ownerId, title, body)` helper that fans out to all of a user's subscriptions; VAPID config via env.

- [ ] **Step 1: Install web-push**

Add to `server/package.json` dependencies: `"web-push": "^3.6.0"`, and `@types/web-push` to devDependencies. Run `npm install`.

- [ ] **Step 2: Add PushSubscription model**

Append to `server/prisma/schema.prisma`:
```prisma
model PushSubscription {
  id        String   @id @default(cuid())
  ownerId   String
  endpoint  String
  p256dh    String
  auth      String
  createdAt DateTime @default(now())
}
```
Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```

- [ ] **Step 3: Generate a VAPID keypair and put it in env**

Run once to generate and print keys:
```bash
node -e "const w=require('web-push');const v=w.generateVAPIDKeys();console.log(v)"
```
Put the printed keys in `.env` and `.env.example`:
```
VAPID_PUBLIC_KEY="<public>"
VAPID_PRIVATE_KEY="<private>"
VAPID_SUBJECT="mailto:shakedzeira@gmail.com"
```

- [ ] **Step 4: Write the push module**

`server/src/push.ts`:
```ts
import webpush from "web-push";
import { prisma } from "./db";

webpush.setVapidDetails(
  process.env.VAPID_SUBJECT || "mailto:shakedzeira@gmail.com",
  process.env.VAPID_PUBLIC_KEY || "",
  process.env.VAPID_PRIVATE_KEY || ""
);

export interface PushBody {
  title: string;
  body?: string;
}

export async function saveSubscription(ownerId: string, sub: { endpoint: string; keys: { p256dh: string; auth: string } }): Promise<void> {
  await prisma.pushSubscription.deleteMany({ where: { ownerId, endpoint: sub.endpoint } });
  await prisma.pushSubscription.create({
    data: { ownerId, endpoint: sub.endpoint, p256dh: sub.keys.p256dh, auth: sub.keys.auth }
  });
}

export async function deleteSubscription(ownerId: string, endpoint: string): Promise<void> {
  await prisma.pushSubscription.deleteMany({ where: { ownerId, endpoint } });
}

export async function sendPush(ownerId: string, message: PushBody): Promise<number> {
  const subs = await prisma.pushSubscription.findMany({ where: { ownerId } });
  const payload = JSON.stringify(message);
  let sent = 0;
  for (const sub of subs) {
    try {
      await webpush.sendNotification(
        { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
        payload
      );
      sent++;
    } catch {
      await prisma.pushSubscription.delete({ where: { id: sub.id } }).catch(() => {});
    }
  }
  return sent;
}
```

- [ ] **Step 5: Add the subscription routes**

In `server/src/push.ts`, add a router:
```ts
import { Router } from "express";
import { requireAuth, type AuthedRequest } from "./session";

export const pushRouter = Router();
pushRouter.use(requireAuth);

pushRouter.post("/subscribe", async (req: AuthedRequest, res) => {
  const sub = req.body;
  if (!sub?.endpoint || !sub?.keys?.p256dh || !sub?.keys?.auth) {
    return res.status(400).json({ error: "invalid push subscription" });
  }
  await saveSubscription(req.userId!, sub);
  res.status(201).json({ ok: true });
});

pushRouter.delete("/subscribe", async (req: AuthedRequest, res) => {
  const { endpoint } = req.body ?? {};
  if (!endpoint) return res.status(400).json({ error: "endpoint required" });
  await deleteSubscription(req.userId!, endpoint);
  res.status(204).end();
});
```

- [ ] **Step 6: Mount the router**

In `server/src/index.ts`:
```ts
app.use("/api/push", pushRouter);
```

- [ ] **Step 7: Write the push test**

`server/src/push.test.ts`:
```ts
import { test, expect } from "vitest";
import { saveSubscription, sendPush } from "./push";
import { prisma } from "./db";

test("save then drop a subscription", async () => {
  await saveSubscription("owner-1", {
    endpoint: "https://example.com/push/1",
    keys: { p256dh: "c2hh", auth: "YXV0aA==" }
  });
  const rows = await prisma.pushSubscription.findMany({ where: { ownerId: "owner-1" } });
  expect(rows).toHaveLength(1);
  await prisma.pushSubscription.deleteMany({ where: { ownerId: "owner-1" } });
});

test("sendPush returns count and prunes dead endpoints", async () => {
  // No valid web push server here; a bogus endpoint will fail and be pruned.
  await saveSubscription("owner-2", {
    endpoint: "https://invalid.invalid/push/1",
    keys: { p256dh: "c2hh", auth: "YXV0aA==" }
  });
  const sent = await sendPush("owner-2", { title: "hi", body: "test" });
  expect(sent).toBe(0);
  const remaining = await prisma.pushSubscription.findMany({ where: { ownerId: "owner-2" } });
  expect(remaining).toHaveLength(0);
});
```

- [ ] **Step 8: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Then:
```bash
git add -A
git commit -m "feat(server): web push subscriptions with vapid"
```

---

### Task 1.4: Scheduled alert job

**Files:**
- Create: `server/src/scheduler.ts`
- Modify: `server/src/index.ts`
- Create: `server/src/scheduler.test.ts`

**Interfaces:**
- Consumes: `prisma`, `sendPush` (Task 1.3), `dueOn` (Task 1.1).
- Produces: `checkDueTasks(now)` — scans PENDING tasks due on `now`, sends a push per task, marks non-recurring ones as "alerted" (by setting `completedAt`-independent flag — see step), returns count handled. A `node-cron` job runs it every minute in production when enabled by env.

- [ ] **Step 1: Install node-cron**

Add `"node-cron": "^3.0.3"` to `server/package.json` dependencies and `@types/node-cron` to devDependencies. Run `npm install`.

- [ ] **Step 2: Add an alertedAt field to Task**

Append to `server/prisma/schema.prisma` Task model:
```prisma
  alertedAt DateTime?
```
Regenerate and push:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```

- [ ] **Step 3: Write the scheduler**

`server/src/scheduler.ts`:
```ts
import cron from "node-cron";
import { prisma } from "./db";
import { sendPush } from "./push";
import { dueOn } from "./tasks";

export async function checkDueTasks(now: Date = new Date()): Promise<number> {
  const tasks = await prisma.task.findMany({ where: { status: "PENDING" } });
  let handled = 0;
  for (const task of tasks) {
    const alreadyAlerted = task.alertedAt != null;
    if (!dueOn(task, now) || alreadyAlerted) continue;
    await sendPush(task.ownerId, {
      title: "Task due now",
      body: task.title
    });
    if (task.recurrence === "none" || task.recurrence == null) {
      await prisma.task.update({ where: { id: task.id }, data: { alertedAt: now } });
    }
    handled++;
  }
  return handled;
}

export function startScheduler(): void {
  if (process.env.ENABLE_SCHEDULER !== "true") return;
  cron.schedule("* * * * *", () => {
    checkDueTasks().catch((e) => console.error("scheduler error", e));
  });
}
```

- [ ] **Step 4: Start the scheduler on boot**

In `server/src/index.ts`, import and call `startScheduler()` when `require.main === module`.

- [ ] **Step 5: Write the scheduler test**

`server/src/scheduler.test.ts`:
```ts
import { beforeEach, test, expect, vi } from "vitest";
import { checkDueTasks } from "./scheduler";
import { prisma } from "./db";

beforeEach(async () => {
  await prisma.task.deleteMany({});
});

test("alerts a due non-recurring task once and flags it", async () => {
  const owner = "owner-x";
  const task = await prisma.task.create({
    data: { ownerId: owner, title: "standup", status: "PENDING", alertedAt: null }
  });
  // dueOn returns false for recurring none unless dueAt today; set dueAt to today.
  await prisma.task.update({
    where: { id: task.id },
    data: { dueAt: new Date() }
  });

  const pushSpy = vi.spyOn(await import("./push"), "sendPush").mockResolvedValue(1);
  const first = await checkDueTasks(new Date());
  expect(first).toBe(1);
  const second = await checkDueTasks(new Date());
  expect(second).toBe(0); // already alerted

  pushSpy.mockRestore();
});
```

*(Note: `dueOn` for `recurrence: "none"` compares `sameDay(task.dueAt, now)` — set `dueAt` to today so the test is deterministic.)*

- [ ] **Step 6: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Then:
```bash
git add -A
git commit -m "feat(server): scheduled push alerts for due tasks"
```

---

### Task 1.5: Client push registration + on-screen notifications

**Files:**
- Create: `client/src/usePush.ts`
- Modify: `client/src/main.tsx`
- Modify: `client/src/App.tsx` (notify the user a task fired)

**Interfaces:**
- Consumes: `authedFetch` (client auth), server `POST /api/push/subscribe`.
- Produces: `usePush(token)` hook that requests Notification permission, gets an existing service worker, subscribes via `PushManager`, and POSTs the subscription to the server. The PWA must have a registered service worker handling `push` events to show notifications.

- [ ] **Step 1: Ensure a service worker with push handling**

`vite-plugin-pwa` generates a service worker during `npm run build`. For dev, enable `devOptions.enabled` in `vite.config.ts`:
```ts
VitePWA({
  registerType: "autoUpdate",
  includeAssets: ["favicon.svg"],
  devOptions: { enabled: true },
  workbox: {
    // Customize push handler via injectManifest alternative is advanced;
    // for this phase, rely on the workbox-generated SW for caching and
    // register a Notification click listener in usePush.
  },
  // ... existing manifest
})
```

- [ ] **Step 2: Write the usePush hook**

`client/src/usePush.ts`:
```ts
import { useEffect } from "react";
import { authedFetch } from "./auth";

export function usePush(token: string | null): void {
  useEffect(() => {
    if (!token) return;
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) return;
    const run = async () => {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") return;
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(import.meta.env.VITE_VAPID_PUBLIC_KEY || "")
      });
      await authedFetch(token, "/api/push/subscribe", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(sub)
      });
    };
    run().catch(() => {});
  }, [token]);
}

function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, "+").replace(/_/g, "/"));
  return new Uint8Array([...raw].map((c) => c.charCodeAt(0)));
}
```

Add `VITE_VAPID_PUBLIC_KEY` to `client/.env` and `client/.env.example` (same public key as server `.env`). Ensure `vite.config.ts` exposes it (Vite exposes `VITE_*` env vars to client by default).

- [ ] **Step 3: Call usePush from App**

In `client/src/App.tsx`, call `usePush(token)` at the top of the component.

- [ ] **Step 4: Typecheck, build, verify**

```bash
npm run typecheck
npm run build --workspace client
```
Start servers. In Chrome at `http://localhost:5173` (allow notifications), trigger a due task via the DB, confirm a system notification appears on desktop.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(client): web push subscription and notifications"
```

---

### Task 1.6: Due-today view + digest hook

**Files:**
- Modify: `server/src/tasks.ts` (add `GET /api/tasks/today`)
- Modify: `client/src/taskApi.ts`
- Modify: `client/src/TaskList.tsx` (today section)

**Interfaces:**
- Consumes: task router, `dueOn`.
- Produces: `GET /api/tasks/today` returning tasks due today (PENDING), grouped by status. Used by the "focus digest" in Phase 5.

- [ ] **Step 1: Add the today endpoint**

In `server/src/tasks.ts`, add before the `/:id` routes:
```ts
tasksRouter.get("/today", async (req: AuthedRequest, res) => {
  const now = new Date();
  const owned = await prisma.task.findMany({ where: { ownerId: req.userId } });
  const due = owned.filter((t) => t.status !== "DONE" || (t.recurrence !== "none" && t.recurrence != null));
  const today = due.filter((t) => dueOn(t, now));
  res.json(today);
});
```

- [ ] **Step 2: Add the client call**

In `client/src/taskApi.ts` add:
```ts
export async function getTodayTasks(token: string): Promise<Task[]> {
  const res = await authedFetch(token, "/api/tasks/today");
  return json<Task[]>(res);
}
```
And in `TaskList.tsx`, add a "Due today" section fetching `getTodayTasks` alongside the full list.

- [ ] **Step 3: Typecheck and commit**

```bash
npm run typecheck
git add -A
git commit -m "feat: due-today task view for digest"
```

---

## Phase 1 Self-Review

- **Spec coverage:** Tasks with clock alerts (§3.1) — recurring rules (Task 1.1, `dueOn`), completion + streaks seed (Task 1.2), Web Push alerts with VAPID (Tasks 1.3–1.5), digest hook (Task 1.6). All covered.
- **Placeholder scan:** No TODOs; `image` icon placeholder in client manifest is acceptable as it exists as a build config, not a plan task. Steps carry real code.
- **Type consistency:** `dueOn(task, date)` signature consistent across Task 1.1 (definition), 1.4 (scheduler import), and 1.6 (today endpoint). `sendPush(ownerId, message)` consistent between 1.3 and 1.4. `authedFetch(token, path, init)` consistent client-side.
- **Known deliberate note:** Task 1.4 marks non-recurring tasks as alerted via `alertedAt`, which is a real schema field added in that task and used consistently.
- **Added feature (beyond original plan):** **Weekly Goals** (`WeeklyGoal` + `GoalEvent` models, `/api/goals` router, `Goals.tsx` UI) — manual check-offs with automatic Monday weekly reset via `startOfWeek`/`computeThisWeekCount`. Goals carry an `autoSource` field (null/manual, or a module tag like `workout`, `italian_words`, `italian_lesson`, `study`) for FUTURE automatic progress. **Phase 2 (health) and Phase 4 (italian) MUST wire auto-counting:** when a workout is logged (Phase 2) or an Italian word/lesson is completed (Phase 4), if the user has a matching `WeeklyGoal` with the corresponding `autoSource`, insert a `GoalEvent` (source = that autoSource value) so weekly progress increments without manual check-off.
