# Phase 5 — Polish & Glue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Tie the Tasks, Health, Study, and Italian modules together with a daily focus digest, a unified streaks + progress dashboard, hardened PWA offline behavior, and final navigation/settings polish.

**Architecture:** Two new server aggregators (`GET /api/digest/today` and `GET /api/progress`) read the existing module tables through `prisma` scoped by `ownerId` — `Task`, `Workout`, `MealLog`/`Food`, `DailyTarget`, `Pdf`, `Highlight`, `Flashcard`, `Lesson`, `PracticeLog` — never redesigning them, and return unified JSON. A new shared `currentStreak` helper reproduces the exact active-day streak semantics the Italian module already uses (Phase 4 Task 4.5). A small key/value `Setting` table backs the settings page. The client gets a `DigestView` landing page, a `ProgressView` dashboard (plain-SVG bar chart, no chart library), glue `HealthView`/`ItalianView` compositions, a responsive bottom/top navigation shell, and a settings view. PWA hardening is done via `vite-plugin-pwa` workbox config (app-shell precache, network-first `/api` runtime caching, offline fallback) and real manifest icons.

**Tech Stack:** Express, Prisma, SQLite, React 18, TypeScript, Tailwind CSS, `vite-plugin-pwa` (workbox), vitest + supertest (server), plain SVG (client charts).

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; lint + typecheck must pass before any commit.
- Single-user app; all rows carry `ownerId`.
- Authed routes use `requireAuth` from `server/src/session.ts`.
- This phase reuses existing modules/tables; do not redesign them, only aggregate. Consumed table shapes below match the Phase 1–4 schemas; typecheck is the guard — if a column name differs, adjust the query/seed to the real name, never the semantics.
- iOS PWA push/notification limitations are a known tradeoff (spec §7) — harden but don't pretend to fix iOS.
- Windows host (cmd.exe); paths use `\` in commands.

---

### Task 5.1: Focus digest aggregation

**Files:**
- Modify: `shared/src/index.ts` (add `DigestResponse` and friends)
- Create: `server/src/streak.ts`
- Create: `server/src/streak.test.ts`
- Create: `server/src/digest.ts` (pure builders + `digestRouter`)
- Create: `server/src/digest.test.ts`
- Modify: `server/src/index.ts` (mount router)

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`; `dueOn(task, date)` exported by `server/src/tasks.ts` (Phase 1 — signature `dueOn(task: { dueAt: Date | null; recurrence: string | null }, date: Date): boolean`). Existing tables read only:
  - `Task` (Phase 1): `id/ownerId/title/category/status/dueAt/recurrence/completedAt`.
  - `Workout` (Phase 2): `id/ownerId/date: DateTime`.
  - `Pdf` (Phase 3): `id/ownerId/title/readProgress: Float/updatedAt` (no due-date concept exists — "study item today" is derived from in-progress reading).
  - `Flashcard` (Phase 3): `id/ownerId/pdfId`.
  - `Lesson` (Phase 4): `id/ownerId/date: String "YYYY-MM-DD"/title/completedAt: DateTime?`.
  - `PracticeLog` (Phase 4): `ownerId/createdAt` (a day is "active" for streak purposes iff it has a `PracticeLog` row or a `Lesson.completedAt` — Phase 4 Task 4.5 semantics).
- Produces: `DigestResponse` shared type; `server/src/streak.ts` exporting `currentStreak(dates: Date[], now?: Date): number`; `server/src/digest.ts` exporting `todayKey(now): string`, `dayRange(now): { start: Date; end: Date }`, `workoutReminder(workoutsThisWeek: number, lastWorkoutDaysAgo: number | null): string | null`, `pickStudyItem(seed: StudySeeds): DigestStudyItem`, `buildDigest(input: DigestInput): DigestResponse`, and `digestRouter` mounted at `/api/digest` with `GET /today`.

- [ ] **Step 1: Write the failing streak tests**

`server/src/streak.test.ts`:
```ts
import { test, expect } from "vitest";
import { currentStreak } from "./streak";

function d(y: number, m: number, day: number): Date {
  return new Date(y, m - 1, day, 12);
}

test("currentStreak is 0 with no active dates", () => {
  expect(currentStreak([], d(2026, 9, 1))).toBe(0);
});

test("currentStreak counts consecutive days ending at the anchor", () => {
  const dates = [d(2026, 9, 1), d(2026, 8, 31), d(2026, 8, 30)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(3);
});

test("currentStreak breaks on a gap", () => {
  const dates = [d(2026, 9, 1), d(2026, 8, 29)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(1);
});

test("currentStreak returns 0 when the anchor day itself is inactive", () => {
  const dates = [d(2026, 8, 31), d(2026, 8, 30)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(0);
});
```

*(The last case deliberately matches the Italian module's Phase 4 semantics exactly: a streak only counts when the anchor day is active, so the digest and the Italian chip never disagree.)*

- [ ] **Step 2: Run streak tests to verify they fail**

Run: `npm test --workspace server`
Expected: FAIL — module `./streak` not found.

- [ ] **Step 3: Write the streak helper**

`server/src/streak.ts`:
```ts
export function dayKey(d: Date): number {
  return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
}

export function currentStreak(dates: Date[], now: Date = new Date()): number {
  const days = new Set<number>(dates.map(dayKey));
  let cursor = dayKey(now);
  let streak = 0;
  while (days.has(cursor)) {
    streak += 1;
    cursor -= 86_400_000;
  }
  return streak;
}
```

- [ ] **Step 4: Run streak tests to verify they pass**

Run: `npm test --workspace server`
Expected: PASS — 4 tests green.

- [ ] **Step 5: Add the shared digest types**

Append to `shared/src/index.ts`:
```ts
export interface DigestTaskItem {
  id: string;
  title: string;
  category: string | null;
  recurrence: string;
  status: "PENDING" | "DONE";
}

export interface DigestHealthItem {
  workoutsThisWeek: number;
  lastWorkoutDaysAgo: number | null;
  reminder: string | null;
}

export interface DigestItalianItem {
  lessonTitle: string | null;
  streak: number;
  totalLessons: number;
}

export interface DigestStudyItem {
  type: "pdf" | "flashcards" | "none";
  title: string | null;
  detail: string;
}

export interface DigestResponse {
  date: string; // local YYYY-MM-DD
  topTask: DigestTaskItem | null;
  tasksToday: DigestTaskItem[];
  health: DigestHealthItem;
  italian: DigestItalianItem;
  study: DigestStudyItem;
}
```

- [ ] **Step 6: Write the failing digest unit tests**

`server/src/digest.test.ts`:
```ts
import { test, expect } from "vitest";
import { buildDigest, workoutReminder, pickStudyItem, todayKey, dayRange } from "./digest";

test("workoutReminder returns null when on track", () => {
  expect(workoutReminder(2, 0)).toBeNull();
  expect(workoutReminder(1, 2)).toBeNull();
});

test("workoutReminder nags when behind", () => {
  expect(workoutReminder(1, 4)).toBe("3+ days since your last workout — schedule a session.");
  expect(workoutReminder(0, null)).toBe("No workouts this week yet — aim for 2.");
});

test("pickStudyItem prefers an in-progress pdf", () => {
  const item = pickStudyItem({ inProgressPdf: { id: "p1", title: "Linear Algebra" }, pdfsInProgress: 2, flashcardCount: 5 });
  expect(item.type).toBe("pdf");
  expect(item.title).toBe("Linear Algebra");
  expect(item.detail).toBe("2 PDF(s) in progress — resume reading");
});

test("pickStudyItem falls back to flashcards when no pdf is in progress", () => {
  const item = pickStudyItem({ inProgressPdf: null, pdfsInProgress: 0, flashcardCount: 3 });
  expect(item.type).toBe("flashcards");
  expect(item.detail).toBe("3 flashcards ready to review");
});

test("pickStudyItem returns none when nothing is queued", () => {
  const item = pickStudyItem({ inProgressPdf: null, pdfsInProgress: 0, flashcardCount: 0 });
  expect(item.type).toBe("none");
});

test("buildDigest assembles a full response", () => {
  const res = buildDigest({
    today: "2026-09-01",
    tasksToday: [
      { id: "t1", title: "Pay rent", category: null, recurrence: "none", status: "PENDING", dueAt: null, completedAt: null }
    ],
    workoutsThisWeek: 1,
    lastWorkoutDaysAgo: 3,
    lessonTitle: "Ciao",
    activeDays: [new Date(2026, 8, 1)],
    totalLessons: 5,
    study: { inProgressPdf: null, pdfsInProgress: 0, flashcardCount: 0 }
  });
  expect(res.topTask?.title).toBe("Pay rent");
  expect(res.tasksToday).toHaveLength(1);
  expect(res.health.reminder).toBe("3+ days since your last workout — schedule a session.");
  expect(res.italian.streak).toBe(1);
  expect(res.italian.totalLessons).toBe(5);
  expect(res.study.type).toBe("none");
});

test("todayKey and dayRange are local-date aligned", () => {
  const now = new Date(2026, 8, 1, 10, 30);
  expect(todayKey(now)).toBe("2026-09-01");
  const range = dayRange(now);
  expect(range.start.getTime()).toBe(new Date(2026, 8, 1).getTime());
  expect(range.end.getTime()).toBe(new Date(2026, 8, 2).getTime());
});
```

- [ ] **Step 7: Run digest unit tests to verify they fail**

Run: `npm test --workspace server`
Expected: FAIL — module `./digest` not found.

- [ ] **Step 8: Write the digest builders**

`server/src/digest.ts`:
```ts
import { Router } from "express";
import type { DigestResponse, DigestTaskItem, DigestStudyItem } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { dueOn } from "./tasks";
import { currentStreak } from "./streak";

export interface TaskRow {
  id: string;
  title: string;
  category: string | null;
  recurrence: string | null;
  status: "PENDING" | "DONE";
  dueAt: Date | null;
  completedAt: Date | null;
}

export function todayKey(now: Date): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function dayRange(now: Date = new Date()): { start: Date; end: Date } {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  return { start, end };
}

export function workoutReminder(workoutsThisWeek: number, lastWorkoutDaysAgo: number | null): string | null {
  if (workoutsThisWeek >= 2) return null;
  if (lastWorkoutDaysAgo === null) return "No workouts this week yet — aim for 2.";
  if (lastWorkoutDaysAgo >= 3) return "3+ days since your last workout — schedule a session.";
  return null;
}

export interface StudySeeds {
  inProgressPdf: { id: string; title: string } | null;
  pdfsInProgress: number;
  flashcardCount: number;
}

export function pickStudyItem(seed: StudySeeds): DigestStudyItem {
  if (seed.inProgressPdf) {
    return {
      type: "pdf",
      title: seed.inProgressPdf.title,
      detail: `${seed.pdfsInProgress} PDF(s) in progress — resume reading`
    };
  }
  if (seed.flashcardCount > 0) {
    return { type: "flashcards", title: null, detail: `${seed.flashcardCount} flashcards ready to review` };
  }
  return { type: "none", title: null, detail: "No study material queued for today" };
}

export interface DigestInput {
  today: string;
  tasksToday: TaskRow[];
  workoutsThisWeek: number;
  lastWorkoutDaysAgo: number | null;
  lessonTitle: string | null;
  activeDays: Date[];
  totalLessons: number;
  study: StudySeeds;
}

export function buildDigest(input: DigestInput): DigestResponse {
  const tasksToday: DigestTaskItem[] = input.tasksToday.map((t) => ({
    id: t.id,
    title: t.title,
    category: t.category,
    recurrence: t.recurrence ?? "none",
    status: t.status
  }));
  return {
    date: input.today,
    topTask: tasksToday.find((t) => t.status === "PENDING") ?? null,
    tasksToday,
    health: {
      workoutsThisWeek: input.workoutsThisWeek,
      lastWorkoutDaysAgo: input.lastWorkoutDaysAgo,
      reminder: workoutReminder(input.workoutsThisWeek, input.lastWorkoutDaysAgo)
    },
    italian: {
      lessonTitle: input.lessonTitle,
      streak: currentStreak(input.activeDays),
      totalLessons: input.totalLessons
    },
    study: pickStudyItem(input.study)
  };
}
```

- [ ] **Step 9: Run digest unit tests to verify they pass**

Run: `npm test --workspace server`
Expected: PASS — all digest unit tests green.

- [ ] **Step 10: Write the failing integration test**

Append to `server/src/digest.test.ts`:
```ts
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { todayKey } from "./digest";

describe("GET /api/digest/today", () => {
  let token = "";
  let ownerId = "";

  beforeEach(async () => {
    await prisma.task.deleteMany({});
    await prisma.workout.deleteMany({});
    await prisma.lesson.deleteMany({});
    await prisma.practiceLog.deleteMany({});
    await prisma.highlight.deleteMany({});
    await prisma.flashcard.deleteMany({});
    await prisma.pdf.deleteMany({});
    await prisma.user.deleteMany({});

    const reg = await request(app)
      .post("/api/auth/register")
      .send({ email: "digest@example.com", password: "password123" });
    token = reg.body.token as string;
    ownerId = reg.body.user.id as string;

    await prisma.task.create({
      data: { ownerId, title: "Pay rent", status: "PENDING", dueAt: new Date() }
    });
    // One workout 4 days ago: inside the last 7 days but >3 days idle.
    await prisma.workout.create({ data: { ownerId, date: new Date(Date.now() - 4 * 86_400_000) } });
    // A completed lesson today plus a practice log today => active day.
    await prisma.lesson.create({
      data: { ownerId, date: todayKey(new Date()), title: "Il caffè", tip: "t", vocab: "[]", phrases: "[]", completedAt: new Date() }
    });
    await prisma.practiceLog.create({
      data: { ownerId, lessonDate: todayKey(new Date()), phraseIndex: 0, isCorrect: true, userAttempt: "ciao" }
    });
    // A PDF mid-reading and flashcards behind it.
    const pdf = await prisma.pdf.create({
      data: { ownerId, title: "Laplace Notes", fileName: "n.pdf", path: "n", size: 1, readProgress: 0.4 }
    });
    await prisma.flashcard.create({ data: { ownerId, pdfId: pdf.id, front: "Ciao", back: "Hello" } });
  });

  test("aggregates today's digest across all modules", async () => {
    const res = await request(app)
      .get("/api/digest/today")
      .set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasksToday).toHaveLength(1);
    expect(res.body.topTask.title).toBe("Pay rent");
    expect(res.body.health.workoutsThisWeek).toBe(1);
    expect(res.body.health.reminder).toBe("3+ days since your last workout — schedule a session.");
    expect(res.body.italian.lessonTitle).toBe("Il caffè");
    expect(res.body.italian.streak).toBe(1);
    expect(res.body.italian.totalLessons).toBe(1);
    expect(res.body.study.type).toBe("pdf");
    expect(res.body.study.title).toBe("Laplace Notes");
  });

  test("rejects unauthenticated access", async () => {
    const res = await request(app).get("/api/digest/today");
    expect(res.status).toBe(401);
  });
});
```

- [ ] **Step 11: Run integration tests to verify they fail**

Run: `npm test --workspace server`
Expected: FAIL — `digestRouter` not mounted (`Cannot GET /api/digest/today`). The preceding unit tests still pass.

- [ ] **Step 12: Write the digest router and mount it**

Append to `server/src/digest.ts`:
```ts
export const digestRouter = Router();
digestRouter.use(requireAuth);

digestRouter.get("/today", async (req: AuthedRequest, res) => {
  const now = new Date();
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const ownerId = req.userId!;

  const [ownedTasks, workoutsThisWeek, lastWorkout, lessonToday, practiceLogs, completedLessons, inProgressPdf, pdfsInProgress, flashcardCount] =
    await Promise.all([
      prisma.task.findMany({ where: { ownerId } }),
      prisma.workout.count({ where: { ownerId, date: { gte: weekAgo } } }),
      prisma.workout.findFirst({ where: { ownerId }, orderBy: { date: "desc" }, select: { date: true } }),
      prisma.lesson.findFirst({ where: { ownerId, date: todayKey(now) }, select: { title: true } }),
      prisma.practiceLog.findMany({ where: { ownerId }, select: { createdAt: true } }),
      prisma.lesson.findMany({ where: { ownerId, completedAt: { not: null } }, select: { completedAt: true } }),
      prisma.pdf.findFirst({
        where: { ownerId, readProgress: { gt: 0, lt: 1 } },
        orderBy: { updatedAt: "desc" },
        select: { id: true, title: true }
      }),
      prisma.pdf.count({ where: { ownerId, readProgress: { gt: 0, lt: 1 } } }),
      prisma.flashcard.count({ where: { ownerId } })
    ]);

  const tasksToday = ownedTasks.filter(
    (t) => (t.status !== "DONE" || (t.recurrence !== "none" && t.recurrence != null)) && dueOn(t, now)
  );
  const lastWorkoutDaysAgo = lastWorkout
    ? Math.max(0, Math.floor((now.getTime() - lastWorkout.date.getTime()) / 86_400_000))
    : null;
  const activeDays = [
    ...practiceLogs.map((l) => l.createdAt),
    ...completedLessons.map((l) => l.completedAt as Date)
  ];

  res.json(
    buildDigest({
      today: todayKey(now),
      tasksToday,
      workoutsThisWeek,
      lastWorkoutDaysAgo,
      lessonTitle: lessonToday?.title ?? null,
      activeDays,
      totalLessons: completedLessons.length,
      study: { inProgressPdf, pdfsInProgress, flashcardCount }
    })
  );
});
```

In `server/src/index.ts`, import and mount:
```ts
import { digestRouter } from "./digest";
// ...
app.use("/api/digest", digestRouter);
```

- [ ] **Step 13: Run all tests, typecheck, lint, commit**

```bash
npm test --workspace server
npm run typecheck
npm run lint
```
Expected: tests PASS, typecheck clean, lint clean. Then:
```bash
git add -A
git commit -m "feat(server): focus digest aggregation with unified streak math"
```

---

### Task 5.2: Focus digest UI

**Files:**
- Create: `client/src/digestApi.ts`
- Create: `client/src/DigestView.tsx`
- Modify: `client/src/App.tsx` (render digest as the landing view)

**Interfaces:**
- Consumes: `authedFetch(token, path, init)` from `client/src/auth.ts`; `DigestResponse` shared type (Task 5.1); server `GET /api/digest/today` (Task 5.1).
- Produces: `getTodayDigest(token: string): Promise<DigestResponse>` in `client/src/digestApi.ts`; `DigestView` (default export, props `{ token: string }`) rendered as the logged-in landing view in `App.tsx`.

- [ ] **Step 1: Write the digest API client**

`client/src/digestApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { DigestResponse } from "shared";

export async function getTodayDigest(token: string): Promise<DigestResponse> {
  const res = await authedFetch(token, "/api/digest/today");
  if (!res.ok) throw new Error("failed to load digest");
  return res.json();
}
```

- [ ] **Step 2: Write the DigestView component**

`client/src/DigestView.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import { getTodayDigest } from "./digestApi";
import type { DigestResponse } from "shared";

interface Props {
  token: string;
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl shadow-sm p-4 flex flex-col gap-2">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h3>
      {children}
    </section>
  );
}

export default function DigestView({ token }: Props) {
  const [digest, setDigest] = useState<DigestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setDigest(await getTodayDigest(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed");
    }
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!digest) return <p className="text-slate-400">Loading your day…</p>;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Today</h2>
        <p className="text-sm text-slate-400">{digest.date}</p>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card title="Focus task">
          {digest.topTask ? (
            <p className="text-lg text-slate-800">{digest.topTask.title}</p>
          ) : (
            <p className="text-slate-400">Nothing pressing — enjoy the day.</p>
          )}
          {digest.tasksToday.length > 1 && (
            <p className="text-xs text-slate-400">{digest.tasksToday.length} tasks due today</p>
          )}
        </Card>
        <Card title="Workout">
          {digest.health.reminder ? (
            <p className="text-slate-800">{digest.health.reminder}</p>
          ) : (
            <p className="text-slate-400">{digest.health.workoutsThisWeek}/2 workouts this week — on track.</p>
          )}
        </Card>
        <Card title="Italian">
          {digest.italian.lessonTitle ? (
            <p className="text-slate-800">{digest.italian.lessonTitle}</p>
          ) : (
            <p className="text-slate-400">No lesson queued today.</p>
          )}
          <p className="text-xs text-slate-400">Streak {digest.italian.streak} days · {digest.italian.totalLessons} lessons</p>
        </Card>
        <Card title="Study">
          {digest.study.type !== "none" ? (
            <>
              <p className="text-slate-800">{digest.study.detail}</p>
              {digest.study.title && <p className="text-xs text-slate-400">{digest.study.title}</p>}
            </>
          ) : (
            <p className="text-slate-400">No study material queued for today.</p>
          )}
        </Card>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Make the digest the landing view**

Modify `client/src/App.tsx`: import `DigestView`, then inside the `token && user` authed branch insert `<DigestView token={token} />` as the first child of the branch (above whatever content earlier phases already render there):
```tsx
import DigestView from "./DigestView";
// ...
{token && user ? (
  <div className="w-full flex flex-col items-center gap-6">
    <DigestView token={token} />
    {/* the rest of the existing authed content from Phases 0-4 stays unchanged */}
  </div>
) : (
  <Login onAuthed={onAuthed} />
)}
```

- [ ] **Step 4: Typecheck, run servers, verify manually**

```bash
npm run typecheck
```
Start both dev servers (`npm run dev --workspace server`, `npm run dev --workspace client`), log in, seed a task due today / a workout / a completed lesson / an in-progress PDF and confirm the digest shows all four cards (tasks, health, italian, study) with correct values.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(client): focus digest landing view"
```

---

### Task 5.3: Unified progress dashboard + settings storage

**Files:**
- Modify: `shared/src/index.ts` (add `ProgressResponse`, `SettingsResponse`)
- Modify: `server/prisma/schema.prisma` (add `Setting` model)
- Create: `server/src/settings.ts`
- Create: `server/src/settings.test.ts`
- Create: `server/src/progress.ts`
- Create: `server/src/progress.test.ts`
- Modify: `server/src/index.ts` (mount both routers)
- Create: `client/src/progressApi.ts`
- Create: `client/src/ProgressView.tsx`

**Interfaces:**
- Consumes: `prisma`; `requireAuth`, `AuthedRequest`; `currentStreak`, `dayKey` from `server/src/streak.ts` (Task 5.1); `dayRange`, `todayKey` from `server/src/digest.ts` (Task 5.1). Existing tables read only (Phase 2–4 shapes): `Task`, `Workout` (`date`), `MealLog` (`grams`, `date`, relation `food`), `Food` (`caloriesPer100`), `DailyTarget` (`ownerId @unique`, `calories Int`), `Pdf` (`readProgress`), `Highlight`, `Flashcard`, `Lesson` (`completedAt`, `createdAt`), `PracticeLog` (`createdAt`).
- Produces: `Setting` Prisma model (`@@unique([ownerId, key])`); `server/src/settings.ts` exporting `settingsRouter` mounted at `/api/settings` with `GET /` → `SettingsResponse` and `PUT /:key` (body `{ value: string }`); `server/src/progress.ts` exporting `consumedCalories(meals: Array<{ grams: number; food: { caloriesPer100: number } }>): number`, `computeTaskProgress(tasks: TaskProgressRow[], now: Date)` (returns `{ total, completedThisWeek, completionRateWeek, streak, completedByDay }`), and `progressRouter` mounted at `/api/progress` with `GET /` → `ProgressResponse`; `client/src/progressApi.ts` exporting `getProgress(token: string): Promise<ProgressResponse>`; `client/src/ProgressView.tsx` default export `{ token: string }` including exported `ActivityChart({ days, labels })`.

- [ ] **Step 1: Add the setting model to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Setting {
  id        String   @id @default(cuid())
  ownerId   String
  key       String
  value     String
  updatedAt DateTime @updatedAt

  @@unique([ownerId, key])
  @@index([ownerId])
}
```
Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: client regenerated; `dev.db` updated; exit 0.

- [ ] **Step 2: Add the shared progress + settings types**

Append to `shared/src/index.ts`:
```ts
export interface ProgressTaskSummary {
  total: number;
  completedThisWeek: number;
  completionRateWeek: number;
  streak: number;
  completedByDay: number[]; // 7 entries, oldest first, ending today
}

export interface ProgressHealthSummary {
  workoutsThisWeek: number;
  workoutsTarget: number;
  caloriesToday: number;
  calorieTarget: number;
}

export interface ProgressStudySummary {
  pdfs: number;
  highlights: number;
  flashcards: number;
  pdfsInProgress: number;
}

export interface ProgressItalianSummary {
  streak: number;
  totalLessons: number;
  lessonsThisWeek: number;
}

export interface ProgressResponse {
  date: string; // local YYYY-MM-DD
  tasks: ProgressTaskSummary;
  health: ProgressHealthSummary;
  study: ProgressStudySummary;
  italian: ProgressItalianSummary;
}

export interface SettingsResponse {
  settings: Record<string, string>;
}
```

- [ ] **Step 3: Write the failing settings tests**

`server/src/settings.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let ownerId = "";

beforeEach(async () => {
  await prisma.setting.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "settings@example.com", password: "password123" });
  token = reg.body.token as string;
  ownerId = reg.body.user.id as string;
});

test("GET settings starts empty", async () => {
  const res = await request(app).get("/api/settings").set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.settings).toEqual({});
});

test("PUT then GET roundtrips a setting", async () => {
  const put = await request(app)
    .put("/api/settings/workoutDays")
    .set("Authorization", `Bearer ${token}`)
    .send({ value: "Mon,Wed,Fri" });
  expect(put.status).toBe(200);

  const get = await request(app).get("/api/settings").set("Authorization", `Bearer ${token}`);
  expect(get.body.settings.workoutDays).toBe("Mon,Wed,Fri");

  const rows = await prisma.setting.findMany({ where: { ownerId } });
  expect(rows).toHaveLength(1);
});

test("PUT rejects missing value", async () => {
  const res = await request(app)
    .put("/api/settings/italianLevel")
    .set("Authorization", `Bearer ${token}`)
    .send({});
  expect(res.status).toBe(400);
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/settings");
  expect(res.status).toBe(401);
});
```

- [ ] **Step 4: Run settings tests to verify they fail**

Run: `npm test --workspace server`
Expected: FAIL — module `./settings` not found.

- [ ] **Step 5: Write the settings module**

`server/src/settings.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { SettingsResponse } from "shared";

export const settingsRouter = Router();
settingsRouter.use(requireAuth);

settingsRouter.get("/", async (req: AuthedRequest, res) => {
  const rows = await prisma.setting.findMany({ where: { ownerId: req.userId } });
  const settings: Record<string, string> = {};
  for (const row of rows) settings[row.key] = row.value;
  const body: SettingsResponse = { settings };
  res.json(body);
});

settingsRouter.put("/:key", async (req: AuthedRequest, res) => {
  const { value } = req.body ?? {};
  if (typeof value !== "string") {
    return res.status(400).json({ error: "value required" });
  }
  const key = String(req.params.key);
  const row = await prisma.setting.upsert({
    where: { ownerId_key: { ownerId: req.userId!, key } },
    create: { ownerId: req.userId!, key, value },
    update: { value }
  });
  res.json(row);
});
```

- [ ] **Step 6: Run settings tests to verify they pass**

Run: `npm test --workspace server`
Expected: PASS — 4 tests green.

- [ ] **Step 7: Write the failing progress tests**

`server/src/progress.test.ts`:
```ts
import { test, expect } from "vitest";
import { computeTaskProgress, consumedCalories } from "./progress";

test("consumedCalories computes grams-scaled kcal and rounds", () => {
  const meals = [
    { grams: 200, food: { caloriesPer100: 165 } },
    { grams: 100, food: { caloriesPer100: 61 } }
  ];
  expect(consumedCalories(meals)).toBe(391); // 330 + 61
});

test("computeTaskProgress counts week, rate, streak, and per-day buckets", () => {
  const now = new Date(2026, 8, 1, 12);
  const doneToday = { status: "DONE" as const, completedAt: new Date(2026, 8, 1, 8), createdAt: new Date(2026, 7, 20), dueAt: null, recurrence: "none" };
  const doneYesterday = { status: "DONE" as const, completedAt: new Date(2026, 7, 31, 8), createdAt: new Date(2026, 7, 20), dueAt: null, recurrence: "none" };
  const pending = { status: "PENDING" as const, completedAt: null, createdAt: new Date(2026, 7, 20), dueAt: new Date(2026, 8, 1), recurrence: "none" };
  const r = computeTaskProgress([doneToday, doneYesterday, pending], now);
  expect(r.total).toBe(3);
  expect(r.completedThisWeek).toBe(2);
  expect(r.completionRateWeek).toBe(1);
  expect(r.streak).toBe(2);
  expect(r.completedByDay).toHaveLength(7);
  expect(r.completedByDay[6]).toBe(1); // today slot
});
```

- [ ] **Step 8: Run progress tests to verify they fail**

Run: `npm test --workspace server`
Expected: FAIL — module `./progress` not found.

- [ ] **Step 9: Write the progress module**

`server/src/progress.ts`:
```ts
import { Router } from "express";
import type { ProgressResponse } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { currentStreak, dayKey } from "./streak";
import { dayRange, todayKey } from "./digest";

// Reuses the Phase 2 calorie formula: kcal = grams / 100 * caloriesPer100, rounded.
export function consumedCalories(meals: { grams: number; food: { caloriesPer100: number } }[]): number {
  return Math.round(meals.reduce((sum, m) => sum + m.food.caloriesPer100 * (m.grams / 100), 0));
}

export interface TaskProgressRow {
  status: "PENDING" | "DONE";
  completedAt: Date | null;
  createdAt: Date;
  dueAt: Date | null;
  recurrence: string;
}

export function computeTaskProgress(
  tasks: TaskProgressRow[],
  now: Date
): { total: number; completedThisWeek: number; completionRateWeek: number; streak: number; completedByDay: number[] } {
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const completedThisWeek = tasks.filter(
    (t) => t.status === "DONE" && t.completedAt != null && t.completedAt >= weekAgo
  ).length;
  const plannedThisWeek = tasks.filter(
    (t) =>
      (t.dueAt != null && t.dueAt >= weekAgo && t.dueAt <= now) ||
      (t.recurrence !== "none" && t.recurrence != null)
  ).length;
  const completionRateWeek = plannedThisWeek === 0 ? 0 : Math.min(1, completedThisWeek / plannedThisWeek);
  const streak = currentStreak(
    tasks.filter((t) => t.completedAt != null).map((t) => t.completedAt as Date),
    now
  );
  const completedByDay: number[] = [];
  for (let i = 6; i >= 0; i--) {
    const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    completedByDay.push(
      tasks.filter((t) => t.status === "DONE" && t.completedAt != null && dayKey(t.completedAt) === dayKey(day)).length
    );
  }
  return { total: tasks.length, completedThisWeek, completionRateWeek, streak, completedByDay };
}

export const progressRouter = Router();
progressRouter.use(requireAuth);

progressRouter.get("/", async (req: AuthedRequest, res) => {
  const now = new Date();
  const range = dayRange(now);
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const ownerId = req.userId!;

  const [tasks, workoutCount, targetRow, meals, pdfCount, highlightCount, flashcardCount, pdfsInProgress, practiceLogs, completedLessons, lessonsThisWeek] =
    await Promise.all([
      prisma.task.findMany({
        where: { ownerId },
        select: { status: true, completedAt: true, createdAt: true, dueAt: true, recurrence: true }
      }),
      prisma.workout.count({ where: { ownerId, date: { gte: weekAgo } } }),
      prisma.dailyTarget.findUnique({ where: { ownerId } }),
      prisma.mealLog.findMany({
        where: { ownerId, date: { gte: range.start, lt: range.end } },
        include: { food: { select: { caloriesPer100: true } } }
      }),
      prisma.pdf.count({ where: { ownerId } }),
      prisma.highlight.count({ where: { ownerId } }),
      prisma.flashcard.count({ where: { ownerId } }),
      prisma.pdf.count({ where: { ownerId, readProgress: { gt: 0, lt: 1 } } }),
      prisma.practiceLog.findMany({ where: { ownerId }, select: { createdAt: true } }),
      prisma.lesson.findMany({ where: { ownerId, completedAt: { not: null } }, select: { completedAt: true, createdAt: true } }),
      prisma.lesson.count({ where: { ownerId, completedAt: { not: null, gte: weekAgo } } })
    ]);

  const activeDays = [
    ...practiceLogs.map((l) => l.createdAt),
    ...completedLessons.map((l) => l.completedAt as Date)
  ];
  const taskProgress = computeTaskProgress(tasks, now);
  const body: ProgressResponse = {
    date: todayKey(now),
    tasks: taskProgress,
    health: {
      workoutsThisWeek: workoutCount,
      workoutsTarget: 2,
      caloriesToday: consumedCalories(meals),
      calorieTarget: targetRow?.calories ?? 2000
    },
    study: {
      pdfs: pdfCount,
      highlights: highlightCount,
      flashcards: flashcardCount,
      pdfsInProgress
    },
    italian: {
      streak: currentStreak(activeDays, now),
      totalLessons: completedLessons.length,
      lessonsThisWeek
    }
  };
  res.json(body);
});
```

Add to `server/src/index.ts`:
```ts
import { settingsRouter } from "./settings";
import { progressRouter } from "./progress";
// ...
app.use("/api/settings", settingsRouter);
app.use("/api/progress", progressRouter);
```

- [ ] **Step 10: Run the progress unit tests to verify they pass**

Run: `npm test --workspace server`
Expected: the `consumedCalories` and `computeTaskProgress` tests PASS.

- [ ] **Step 11: Add the progress integration test**

Append to `server/src/progress.test.ts`:
```ts
import { beforeEach } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { todayKey } from "./digest";

describe("GET /api/progress", () => {
  let token = "";
  let ownerId = "";

  beforeEach(async () => {
    await prisma.setting.deleteMany({});
    await prisma.task.deleteMany({});
    await prisma.workout.deleteMany({});
    await prisma.mealLog.deleteMany({});
    await prisma.food.deleteMany({});
    await prisma.dailyTarget.deleteMany({});
    await prisma.highlight.deleteMany({});
    await prisma.flashcard.deleteMany({});
    await prisma.pdf.deleteMany({});
    await prisma.practiceLog.deleteMany({});
    await prisma.lesson.deleteMany({});
    await prisma.user.deleteMany({});

    const reg = await request(app)
      .post("/api/auth/register")
      .send({ email: "progress@example.com", password: "password123" });
    token = reg.body.token as string;
    ownerId = reg.body.user.id as string;

    await prisma.dailyTarget.create({ data: { ownerId, calories: 2500 } });
    await prisma.workout.create({ data: { ownerId, date: new Date() } });
    const food = await prisma.food.create({ data: { ownerId, name: "Chicken Breast", caloriesPer100: 165 } });
    await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 200, date: new Date() } });
    const pdf = await prisma.pdf.create({
      data: { ownerId, title: "Laplace Notes", fileName: "n.pdf", path: "n", size: 1 }
    });
    await prisma.highlight.create({ data: { ownerId, pdfId: pdf.id, pageNumber: 1, text: "transform pair" } });
    await prisma.flashcard.create({ data: { ownerId, pdfId: pdf.id, front: "What is X?", back: "Y" } });
    await prisma.lesson.create({
      data: { ownerId, date: todayKey(new Date()), title: "Gli articoli", tip: "t", vocab: "[]", phrases: "[]", completedAt: new Date() }
    });
    await prisma.practiceLog.create({
      data: { ownerId, lessonDate: todayKey(new Date()), phraseIndex: 0, isCorrect: true, userAttempt: "ciao" }
    });
  });

  test("aggregates counts, streaks, and reuse of DailyTarget", async () => {
    const res = await request(app)
      .get("/api/progress")
      .set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasks).toMatchObject({ total: 0, completionRateWeek: 0, streak: 0 });
    expect(res.body.health).toMatchObject({ workoutsThisWeek: 1, workoutsTarget: 2, caloriesToday: 330, calorieTarget: 2500 });
    expect(res.body.study).toMatchObject({ pdfs: 1, highlights: 1, flashcards: 1, pdfsInProgress: 0 });
    expect(res.body.italian).toMatchObject({ streak: 1, totalLessons: 1, lessonsThisWeek: 1 });
  });

  test("rejects unauthenticated access", async () => {
    const res = await request(app).get("/api/progress");
    expect(res.status).toBe(401);
  });
});
```

- [ ] **Step 12: Run progress integration tests to verify they pass**

Run: `npm test --workspace server`
Expected: PASS. (The `consumedCalories` assertion of 330 — `165 * 200/100` — and the seeded daily target of 2500 are read straight from the Phase 2 tables.)

- [ ] **Step 13: Write the client progress API and view**

`client/src/progressApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { ProgressResponse } from "shared";

export async function getProgress(token: string): Promise<ProgressResponse> {
  const res = await authedFetch(token, "/api/progress");
  if (!res.ok) throw new Error("failed to load progress");
  return res.json();
}
```

`client/src/ProgressView.tsx`:
```tsx
import { useEffect, useState } from "react";
import { getProgress } from "./progressApi";
import type { ProgressResponse } from "shared";

interface Props {
  token: string;
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white rounded-xl shadow-sm p-4 flex flex-col gap-1">
      <span className="text-xs uppercase tracking-wide text-slate-400">{label}</span>
      <span className="text-2xl font-bold text-slate-800">{value}</span>
      {sub && <span className="text-xs text-slate-500">{sub}</span>}
    </div>
  );
}

const DAY_LABELS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

function last7Labels(now: Date): string[] {
  const out: string[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    out.push(DAY_LABELS[d.getDay()]);
  }
  return out;
}

export function ActivityChart({ days, labels }: { days: number[]; labels: string[] }) {
  const max = Math.max(1, ...days);
  return (
    <div className="bg-white rounded-xl shadow-sm p-4">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400 mb-2">Tasks completed</h3>
      <svg viewBox="0 0 140 40" className="w-full" role="img" aria-label="Tasks completed in the last 7 days">
        {days.map((v, i) => {
          const h = (v / max) * 34;
          return <rect key={i} x={i * 20} y={40 - h} width={14} height={h} rx={2} fill="#4f46e5" />;
        })}
      </svg>
      <div className="flex justify-between text-[10px] text-slate-400">
        {labels.map((l, i) => (
          <span key={i} style={{ width: 14 }}>{l}</span>
        ))}
      </div>
    </div>
  );
}

export default function ProgressView({ token }: Props) {
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProgress(token)
      .then(setProgress)
      .catch((e) => setError(e instanceof Error ? e.message : "failed"));
  }, [token]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!progress) return <p className="text-slate-400">Loading progress…</p>;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold text-slate-800">Progress</h2>
      <div className="grid grid-cols-2 gap-4">
        <Stat label="Task streak" value={`${progress.tasks.streak}d`} sub={`${progress.tasks.total} total tasks`} />
        <Stat label="Week completion" value={`${Math.round(progress.tasks.completionRateWeek * 100)}%`} />
        <Stat label="Workouts this week" value={`${progress.health.workoutsThisWeek}/${progress.health.workoutsTarget}`} />
        <Stat label="Italian streak" value={`${progress.italian.streak}d`} sub={`${progress.italian.totalLessons} lessons`} />
        <Stat label="Calories today" value={`${Math.round(progress.health.caloriesToday)}`} sub={`target ${progress.health.calorieTarget}`} />
        <Stat label="PDFs in progress" value={`${progress.study.pdfsInProgress}`} sub={`${progress.study.flashcards} flashcards`} />
      </div>
      <ActivityChart days={progress.tasks.completedByDay} labels={last7Labels(new Date())} />
    </div>
  );
}
```

- [ ] **Step 14: Run tests, typecheck, lint, verify UI, commit**

```bash
npm test --workspace server
npm run typecheck
npm run lint
```
Expected: all green. Then:
```bash
git add -A
git commit -m "feat: unified progress dashboard and settings storage"
```

---

### Task 5.4: Offline hardening and PWA polish

**Files:**
- Create: `client/scripts/gen-icons.mjs`
- Modify: `client/package.json` (add `icons` script)
- Modify: `client/vite.config.ts` (manifest icons + workbox runtime caching + offline fallback)
- Modify: `README.md` (iOS/PWA and offline notes)

**Interfaces:**
- Consumes: the existing `VitePWA` plugin config from Phase 0 (`client/vite.config.ts`); `client/public/favicon.svg`.
- Produces: real `client/public/pwa-192x192.png` and `client/public/pwa-512x512.png`; an updated PWA config that precaches the app shell, routes offline navigations to `index.html`, and caches `/api` GET responses network-first. iOS caveats documented in a config comment and the README.

- [ ] **Step 1: Write the icon generator script**

`client/scripts/gen-icons.mjs`:
```js
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function solidPng(size, [r, g, b]) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  const stride = 1 + size * 4;
  const raw = Buffer.alloc(size * stride);
  for (let y = 0; y < size; y++) {
    const row = y * stride;
    for (let x = 0; x < size; x++) {
      const i = row + 1 + x * 4;
      raw[i] = r;
      raw[i + 1] = g;
      raw[i + 2] = b;
      raw[i + 3] = 255;
    }
  }
  const idat = deflateSync(raw);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", idat),
    chunk("IEND", Buffer.alloc(0))
  ]);
}

const publicDir = join(process.cwd(), "public");
mkdirSync(publicDir, { recursive: true });
writeFileSync(join(publicDir, "pwa-192x192.png"), solidPng(192, [79, 70, 229]));
writeFileSync(join(publicDir, "pwa-512x512.png"), solidPng(512, [79, 70, 229]));
console.log("icons written to client/public");
```

- [ ] **Step 2: Add the icons npm script**

In `client/package.json` `scripts`, add:
```json
"icons": "node scripts/gen-icons.mjs"
```
Run `npm run icons --workspace client` and confirm `client/public/pwa-192x192.png` and `client/public/pwa-512x512.png` exist.

- [ ] **Step 3: Verify the baseline build lacks the new caching**

```bash
npm run build --workspace client
```
Search `client/dist/sw.js` for `api-cache-v1` — Expected: **not found** (the new runtime-cache config is not present yet).

- [ ] **Step 4: Update the PWA config**

Replace the `VitePWA({ ... })` block in `client/vite.config.ts`:
```ts
VitePWA({
  registerType: "autoUpdate",
  includeAssets: ["favicon.svg", "pwa-192x192.png", "pwa-512x512.png"],
  manifest: {
    name: "Day To Day",
    short_name: "DayToDay",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#4f46e5",
    description: "Personal daily companion",
    icons: [
      { src: "/pwa-192x192.png", sizes: "192x192", type: "image/png" },
      { src: "/pwa-512x512.png", sizes: "512x512", type: "image/png" },
      { src: "/pwa-512x512.png", sizes: "512x512", type: "image/png", purpose: "any maskable" }
    ]
  },
  workbox: {
    globPatterns: ["**/*.{js,css,html,svg,png,ico,webmanifest}"],
    // Offline: any navigation that does not hit /api falls back to the app shell.
    navigateFallback: "/index.html",
    // Never serve index.html for API calls; those go through the runtime cache below.
    navigateFallbackDenylist: [/^\/api/],
    runtimeCaching: [
      {
        // Network-first for API GETs: fresh when online, stale replay when offline.
        // Mutations (POST/PUT/DELETE) are never cached — workbox-build registers
        // runtime routes for the GET method only.
        urlPattern: ({ url }) => url.pathname.startsWith("/api"),
        handler: "NetworkFirst",
        options: {
          cacheName: "api-cache-v1",
          networkTimeoutSeconds: 3,
          expiration: { maxEntries: 100, maxAgeSeconds: 60 * 60 },
          cacheableResponse: { statuses: [0, 200] }
        }
      }
    ]
  },
  devOptions: { enabled: true }
})
```

> **iOS caveat (documented, not fixed):** iOS Safari only shows Web Push for installed home-screen PWAs and can evict the service worker after ~7 days of non-use (spec §7). `registerType: "autoUpdate"` and precaching keep the app shell launchable offline, but background notifications on iOS remain a platform limitation.

- [ ] **Step 5: Rebuild and verify the generated service worker + manifest**

```bash
npm run build --workspace client
```
Verify:
- `client/dist/sw.js` contains `api-cache-v1` and the `NetworkFirst` strategy.
- `client/dist/manifest.webmanifest` lists both `pwa-192x192.png` and `pwa-512x512.png`.
- `client/dist/index.html` is present (served as the offline fallback).

- [ ] **Step 6: Add the iOS/PWA note to the README**

In `README.md`, add a section:
```markdown
## PWA & offline notes

- The app precaches its shell and serves it offline via `vite-plugin-pwa` (workbox).
- `/api` GET requests are cached network-first (`api-cache-v1`, 1h TTL); POST/PUT/DELETE are never cached.
- Install from the address bar / Add to Home Screen. Icons: `client/public/pwa-192x192.png`, `pwa-512x512.png` (regenerate with `npm run icons --workspace client`).
- iOS Safari: push notifications only work for installed home-screen PWAs and may stop after ~7 days of no use (spec §7). Background push on iOS is a known limitation, not a bug to fix.
```

- [ ] **Step 7: Verify dev preview and commit**

```bash
npm run typecheck
npm run preview --workspace client
```
Open the preview URL, use DevTools > Network > Offline, and reload — Expected: the app shell loads with previously fetched digest data. Then:
```bash
git add -A
git commit -m "feat(client): pwa offline hardening and manifest icons"
```

---

### Task 5.5: Final polish — navigation, settings UI, README refresh, smoke test

**Files:**
- Create: `client/src/Nav.tsx`
- Create: `client/src/settingsApi.ts`
- Create: `client/src/SettingsView.tsx`
- Create: `client/src/HealthView.tsx` (glue, composes the Phase 2 components)
- Create: `client/src/ItalianView.tsx` (glue, composes the Phase 4 lesson + progress chip)
- Modify: `client/src/App.tsx` (navigation shell + view routing)
- Modify: `README.md` (deploy note refresh)

**Interfaces:**
- Consumes:
  - `DigestView` (Task 5.2), `ProgressView` (Task 5.3), `TaskList` (Phase 1 — default export, `{ token: string }`).
  - Phase 2 components (default exports): `Workouts({ token })`, `Foods({ token, meals, onChanged })`, `NutritionSummary({ summary })`, `PhotoCalories({ token })`; `nutritionApi.ts` exports `getSummary(token, date?)` and `setTarget(token, calories)`.
  - Phase 3 `PdfHub({ token })` (default export).
  - Phase 4 `ItalianLesson({ token, streak?, totalLessons? })` (default export); `italianApi.ts` exports `getProgress(token): Promise<ItalianProgress>`.
  - Server `GET /api/settings` and `PUT /api/settings/:key` (Task 5.3).
- Produces: `Nav` (default export, `{ view: string; onNavigate: (view: string) => void }`) — bottom nav on mobile, top nav on desktop; `settingsApi.ts` exporting `getSettings(token): Promise<SettingsResponse>` and `putSetting(token, key, value): Promise<void>`; `SettingsView` (default export, `{ token: string }`) which persists calorie target via the existing nutrition target API and the other keys via settings; `HealthView`/`ItalianView` glue views; `App.tsx` routes between `today | tasks | health | study | italian | progress | settings`; refreshed README; documented end-to-end smoke test.

- [ ] **Step 1: Write the navigation shell**

`client/src/Nav.tsx`:
```tsx
interface NavItem {
  key: string;
  label: string;
}

const ITEMS: NavItem[] = [
  { key: "today", label: "Today" },
  { key: "tasks", label: "Tasks" },
  { key: "health", label: "Health" },
  { key: "study", label: "Study" },
  { key: "italian", label: "Italian" },
  { key: "progress", label: "Progress" },
  { key: "settings", label: "Settings" }
];

interface Props {
  view: string;
  onNavigate: (view: string) => void;
}

export default function Nav({ view, onNavigate }: Props) {
  const base = "px-3 py-2 text-sm font-medium rounded";
  const active = "bg-indigo-600 text-white";
  const idle = "text-slate-600 hover:bg-slate-200";
  return (
    <nav className="fixed bottom-0 inset-x-0 bg-white border-t md:static md:border-y">
      <ul className="flex md:flex-wrap md:gap-1 md:px-4 md:py-2">
        {ITEMS.map((item) => (
          <li key={item.key} className="flex-1 md:flex-none">
            <button
              className={`w-full md:w-auto ${base} ${view === item.key ? active : idle}`}
              onClick={() => onNavigate(item.key)}
            >
              {item.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
```

- [ ] **Step 2: Write the settings API client**

`client/src/settingsApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { SettingsResponse } from "shared";

export async function getSettings(token: string): Promise<SettingsResponse> {
  const res = await authedFetch(token, "/api/settings");
  if (!res.ok) throw new Error("failed to load settings");
  return res.json();
}

export async function putSetting(token: string, key: string, value: string): Promise<void> {
  const res = await authedFetch(token, `/api/settings/${encodeURIComponent(key)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ value })
  });
  if (!res.ok) throw new Error("failed to save setting");
}
```

- [ ] **Step 3: Write the settings view**

`client/src/SettingsView.tsx`:
```tsx
import { useEffect, useState } from "react";
import { getSettings, putSetting } from "./settingsApi";
import { getSummary, setTarget } from "./nutritionApi";

interface Props {
  token: string;
}

export default function SettingsView({ token }: Props) {
  const [settings, setSettings] = useState<Record<string, string> | null>(null);
  const [calorieTarget, setCalorieTarget] = useState<string>("");

  useEffect(() => {
    getSettings(token)
      .then((res) => setSettings(res.settings))
      .catch(() => setSettings({}));
    getSummary(token)
      .then((s) => setCalorieTarget(String(s.target)))
      .catch(() => setCalorieTarget(""));
  }, [token]);

  async function save(key: string, value: string) {
    await putSetting(token, key, value);
    setSettings((prev) => ({ ...(prev ?? {}), [key]: value }));
  }

  async function saveCalories(value: string) {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) return;
    await setTarget(token, n);
    setCalorieTarget(String(n));
  }

  if (!settings) return <p className="text-slate-400">Loading settings…</p>;

  const workoutDays = settings.workoutDays ?? "Mon,Wed,Fri";
  const italianLevel = settings.italianLevel ?? "A1";
  const notificationsEnabled = settings.notificationsEnabled === "true";

  return (
    <div className="flex flex-col gap-4 w-full max-w-lg">
      <h2 className="text-2xl font-bold text-slate-800">Settings</h2>
      <label className="flex flex-col gap-1 text-sm">
        Daily calorie target (kcal)
        <input
          type="number"
          className="border rounded px-3 py-2"
          value={calorieTarget}
          onChange={(e) => setCalorieTarget(e.target.value)}
          onBlur={(e) => saveCalories(e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Workout days (comma-separated, e.g. Mon,Wed,Fri)
        <input
          className="border rounded px-3 py-2"
          value={workoutDays}
          onChange={(e) => save("workoutDays", e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Italian level
        <select
          className="border rounded px-3 py-2"
          value={italianLevel}
          onChange={(e) => save("italianLevel", e.target.value)}
        >
          <option value="A1">A1</option>
          <option value="A2">A2</option>
          <option value="B1">B1</option>
        </select>
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={notificationsEnabled}
          onChange={(e) => save("notificationsEnabled", String(e.target.checked))}
        />
        Enable push notifications
      </label>
      <p className="text-xs text-slate-400">
        iOS Safari push only works for installed home-screen PWAs (spec §7).
      </p>
    </div>
  );
}
```

- [ ] **Step 4: Write the Health and Italian glue views**

`client/src/HealthView.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import Workouts from "./Workouts";
import Foods from "./Foods";
import NutritionSummary from "./NutritionSummary";
import PhotoCalories from "./PhotoCalories";
import { getSummary } from "./nutritionApi";
import type { NutritionSummary as NutritionSummaryData } from "shared";

interface Props {
  token: string;
}

export default function HealthView({ token }: Props) {
  const [summary, setSummary] = useState<NutritionSummaryData | null>(null);

  const refreshNutrition = useCallback(async () => {
    try {
      setSummary(await getSummary(token));
    } catch {
      setSummary(null);
    }
  }, [token]);

  useEffect(() => { refreshNutrition(); }, [refreshNutrition]);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold text-slate-800">Health</h2>
      <Workouts token={token} />
      <Foods token={token} meals={summary?.meals ?? []} onChanged={refreshNutrition} />
      {summary && <NutritionSummary summary={summary} />}
      <PhotoCalories token={token} />
    </div>
  );
}
```

`client/src/ItalianView.tsx`:
```tsx
import { useEffect, useState } from "react";
import ItalianLesson from "./ItalianLesson";
import { getProgress } from "./italianApi";

interface Props {
  token: string;
}

export default function ItalianView({ token }: Props) {
  const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

  useEffect(() => {
    getProgress(token)
      .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
      .catch(() => setProgress(null));
  }, [token]);

  return <ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />;
}
```

- [ ] **Step 5: Rewrite App.tsx as the navigation shell**

`client/src/App.tsx` (full file — replaces the accumulated authed-branch content from Phases 0–4, keeping every module reachable):
```tsx
import { useEffect, useState } from "react";
import Login from "./Login";
import { fetchHealth } from "./api";
import type { AuthUser } from "./auth";
import TaskList from "./TaskList";
import DigestView from "./DigestView";
import ProgressView from "./ProgressView";
import SettingsView from "./SettingsView";
import HealthView from "./HealthView";
import ItalianView from "./ItalianView";
import PdfHub from "./PdfHub";
import Nav from "./Nav";

const TOKEN_KEY = "dtd.token";
const USER_KEY = "dtd.user";

export default function App() {
  const [backend, setBackend] = useState<string>("connecting…");
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  });
  const [view, setView] = useState<string>("today");

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
    setView("today");
  }

  function onLogout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken(null);
    setUser(null);
  }

  const signedIn = token && user;

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col">
      <header className="w-full bg-white shadow-sm px-4 py-3 flex items-center justify-between">
        <h1 className="text-lg font-bold text-slate-800">Day To Day</h1>
        {signedIn && (
          <span className="flex items-center gap-3 text-sm">
            <span className="hidden sm:inline text-slate-500">{user.email}</span>
            <span className="text-xs text-slate-400">Backend: {backend}</span>
            <button className="text-red-600 underline" onClick={onLogout}>Log out</button>
          </span>
        )}
      </header>

      {signedIn ? (
        <>
          <main className="flex-1 w-full max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-6">
            {view === "today" && <DigestView token={token} />}
            {view === "tasks" && <TaskList token={token} />}
            {view === "health" && <HealthView token={token} />}
            {view === "study" && <PdfHub token={token} />}
            {view === "italian" && <ItalianView token={token} />}
            {view === "progress" && <ProgressView token={token} />}
            {view === "settings" && <SettingsView token={token} />}
          </main>
          <Nav view={view} onNavigate={setView} />
        </>
      ) : (
        <main className="flex-1 flex items-center justify-center p-6">
          <Login onAuthed={onAuthed} />
        </main>
      )}
    </div>
  );
}
```

- [ ] **Step 6: Refresh the README**

In `README.md`, update the module list to mention the digest and progress dashboards, and refresh the deploy note: "Deploy two services — the API (`server/`, port 4000) and the static PWA (`client/`) — with the client nginx `try_files` fallback to `index.html` for offline/PWA routing."

- [ ] **Step 7: Typecheck, lint, manual smoke test**

```bash
npm run typecheck
npm run lint
```
Start both dev servers and walk the full smoke test:
1. Register/log in — lands on **Today** digest with all four cards.
2. **Tasks**: add a task due today, complete it; reopen **Today** → top task updates.
3. **Health**: log a workout + a meal (and set a calorie target); open **Progress** → workouts/calories/target reflect it.
4. **Study**: open **PdfHub**, upload/study a PDF (highlights update its progress); **Progress** PDF/flashcard counts update.
5. **Italian**: complete today's lesson; **Today** shows it and **Progress** and the Italian chip both show the streak.
6. **Settings**: change the calorie target → reopen **Progress** → `calorieTarget` reflects it; change workout days → persists after reload.
7. **Offline**: `npm run build --workspace client && npm run preview --workspace client`, toggle offline in DevTools, reload → app shell loads.
8. **Mobile layout**: DevTools responsive mode (<640px) → bottom nav visible; desktop → top nav.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: navigation shell, settings view, readme and smoke polish"
```

---

## Phase 5 Self-Review

- **Spec coverage:** §3.5 bonus — focus digest (Tasks 5.1–5.2) and the unified streaks + progress dashboard (Task 5.3) directly address the "no feedback/progress view" pain; Phase 5 roadmap row — offline hardening + iOS/PWA polish (Task 5.4) and navigation/settings/final pass (Task 5.5). §7 known tradeoff (iOS push) is documented, not "fixed". Tasks/Health/Study/Italian are aggregated only — no model is redesigned; every query reads the Phase 1–4 schemas verbatim. All covered.
- **Placeholder scan:** No TBD/TODO/"implement later". Every step carries runnable code. Cross-phase consumption uses the real shapes found in the Phase 2–4 plans (`Workout.date`, `MealLog`/`Food`, `DailyTarget`, `Pdf.readProgress`, `Highlight`, `Flashcard`, `Lesson.date`/`completedAt`, `PracticeLog.createdAt`, `PdfHub`, `Workouts`, `Foods`, `NutritionSummary`, `PhotoCalories`, `ItalianLesson`) — no invented columns or a `dueAt` that does not exist.
- **Type consistency:** `currentStreak(dates, now?)` / `dayKey` defined in 5.1, reused in 5.3, and ground-matched to Phase 4's own progress algorithm (no grace day). `todayKey`/`dayRange` defined in 5.1, imported by `progress.ts` in 5.3. `buildDigest` input keys (`activeDays`, `study: StudySeeds`) match both the router's constructed object and the shared `DigestResponse`/`DigestItalianItem`/`DigestStudyItem` shapes. `computeTaskProgress` returns exactly `ProgressTaskSummary` (incl. `completedByDay`). `consumedCalories` matches the Phase 2 nutrition formula and its test value (330 kcal from 200g @ 165/100g). `settingsRouter` produced in 5.3 is consumed by `settingsApi.ts`/`SettingsView.tsx` in 5.5; calorie target reuses `nutritionApi.setTarget`. `DigestView`/`ProgressView`/`HealthView`/`ItalianView` props `{ token: string }` match the `App.tsx` call sites; `Nav` props match the call site; `putSetting` path matches server `PUT /api/settings/:key`.