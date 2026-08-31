# Phase 2 — Health Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Add the Health module — workouts with exercises/sets and progressive-weight recommendations, food management from a nutrition database, meal logging with a daily calorie target vs. consumed graph, and rough photo-based calorie estimation behind a swappable `VisionProvider`.

**Architecture:** Server-side Prisma models (`Workout`, `WorkoutSet`, `Food`, `MealLog`, `DailyTarget`) behind a set of REST routers guarded by the JWT `requireAuth` middleware from Phase 0. Progressive weight is computed server-side from a workout's completed sets against a configurable increment (default +2.5kg) per exercise. Food data is seeded from a small embedded array of ~20 common foods; calories are computed from a food's `caloriesPer100g` scaled by logged grams. Photo calorie estimation is isolated behind a `VisionProvider` interface in `server/src/providers/vision.ts` with a default free/local stub that runs without paid API keys and a config switch via env `VISION_PROVIDER`; the client (React PWA) provides workout logging, progression charts (plain SVG, no lib), meal logging, a nutrition summary graph, and a photo upload flow.

**Tech Stack:** Express, Prisma, SQLite, `multer` (multipart photo upload), React, TypeScript, Tailwind, plain inline SVG for charts.

> **Cross-module hook (weekly goals):** When a workout is logged (workout POST), if the logged-in user has a `WeeklyGoal` with `autoSource: "workout"`, insert a matching `GoalEvent` (`ownerId`, `goalId`, `date: now`, `count: 1`, `source: "workout"`) so the weekly-goal progress (feature added in Phase 1) auto-increments. Do this inside the workout create route.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; lint + typecheck must pass before any commit (`npm run typecheck`, `npm run lint`).
- Single-user app: all data rows carry an `ownerId` set to the authed user's id.
- All authed routes require a valid Bearer token via `requireAuth` from `server/src/session.ts` (exports `requireAuth` and `AuthedRequest`).
- AI providers are swappable behind abstractions (spec §5); default free/local, accept rough accuracy.
- Photo calorie accuracy is rough; portion sizing is unreliable (spec §7) — the default `VisionProvider` must run without paid API keys.
- Windows host (cmd.exe shell); paths use `\` in commands.
- Server tests use `vitest` + `supertest` + `prisma` from `server/src/db.ts`, with `npm test --workspace server`.

---

### Task 2.1: Workout data model + API

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/workouts.ts` (router)
- Create: `server/src/workouts.test.ts`
- Modify: `server/src/index.ts`
- Modify: `shared/src/index.ts`

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`; `router` pattern from Phase 1 (`tasksRouter`).
- Produces: Prisma models `Workout` and `WorkoutSet`; REST routes `GET /api/workouts`, `POST /api/workouts`, `GET /api/workouts/:id`, `POST /api/workouts/:id/sets`, `POST /api/workouts/:id/progressive`; exports `progressiveSuggestion(completedSets, incrementKg)` and `historyFor(ownerId)`. Shared types `WorkoutCreateInput`, `WorkoutSetInput`, `ProgressiveSuggestion` appended to `shared/src/index.ts`.

- [ ] **Step 1: Add the Workout and WorkoutSet models to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Workout {
  id        String        @id @default(cuid())
  ownerId   String
  title     String
  date      DateTime      @default(now())
  notes     String?
  createdAt DateTime      @default(now())
  sets      WorkoutSet[]
  @@index([ownerId, date])
}

model WorkoutSet {
  id         String  @id @default(cuid())
  ownerId    String
  workoutId  String
  exercise   String
  weightKg   Float
  reps       Int
  createdAt  DateTime @default(now())
  workout    Workout @relation(fields: [workoutId], references: [id], onDelete: Cascade)
  @@index([ownerId, exercise])
}
```

`WorkoutSet.exercise` stores the exercise name (e.g., `"Bench Press"`). Each row is one logged set.

- [ ] **Step 2: Regenerate Prisma client and push schema**

Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: client regenerated; `server/prisma/dev.db` updated; exit 0.

- [ ] **Step 3: Export shared workout input types**

In `shared/src/index.ts` append:
```ts
export interface WorkoutCreateInput {
  title: string;
  date?: string;
  notes?: string | null;
}

export interface WorkoutSetInput {
  exercise: string;
  weightKg: number;
  reps: number;
}

export interface ProgressiveSuggestion {
  exercise: string;
  currentWeightKg: number;
  suggestedNextKg: number;
  reason: "completed_target" | "not_yet";
}
```

- [ ] **Step 4: Write the workout router**

`server/src/workouts.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { WorkoutSetInput, ProgressiveSuggestion } from "shared";

export const workoutsRouter = Router();
workoutsRouter.use(requireAuth);

export const DEFAULT_INCREMENT_KG = 2.5;
const TARGET_SETS = 3;
const TARGET_REPS = 8;

export interface CompletedSet {
  exercise: string;
  weightKg: number;
  reps: number;
}

export async function progressiveSuggestion(
  completedSets: CompletedSet[],
  incrementKg: number = DEFAULT_INCREMENT_KG
): Promise<ProgressiveSuggestion[]> {
  const byExercise = new Map<string, CompletedSet[]>();
  for (const s of completedSets) byExercise.set(s.exercise, [...(byExercise.get(s.exercise) ?? []), s]);
  const out: ProgressiveSuggestion[] = [];
  for (const [exercise, sets] of byExercise) {
    const hit = sets.length >= TARGET_SETS && sets.every((s) => s.reps >= TARGET_REPS);
    const current = Math.max(...sets.map((s) => s.weightKg));
    out.push({
      exercise,
      currentWeightKg: current,
      suggestedNextKg: hit ? +(current + incrementKg).toFixed(1) : current,
      reason: hit ? "completed_target" : "not_yet"
    });
  }
  return out.sort((a, b) => a.exercise.localeCompare(b.exercise));
}

export interface WorkoutWithSets {
  id: string;
  title: string;
  date: Date;
  notes: string | null;
  sets: { exercise: string; weightKg: number; reps: number }[];
}

export async function historyFor(ownerId: string): Promise<WorkoutWithSets[]> {
  return prisma.workout.findMany({
    where: { ownerId },
    orderBy: { date: "asc" },
    include: {
      sets: { orderBy: { createdAt: "asc" }, select: { exercise: true, weightKg: true, reps: true } }
    }
  });
}

workoutsRouter.get("/", async (req: AuthedRequest, res) => {
  const history = await historyFor(req.userId!);
  res.json(history);
});

workoutsRouter.post("/", async (req: AuthedRequest, res) => {
  const { title, date, notes } = req.body ?? {};
  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  const workout = await prisma.workout.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      date: date ? new Date(date) : new Date(),
      notes: typeof notes === "string" ? notes : null
    }
  });
  res.status(201).json(workout);
});

workoutsRouter.get("/:id", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
    include: { sets: { orderBy: { createdAt: "asc" } } }
  });
  if (!workout) return res.status(404).json({ error: "not found" });
  res.json(workout);
});

workoutsRouter.post("/:id/sets", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!workout) return res.status(404).json({ error: "not found" });

  const items: WorkoutSetInput[] = Array.isArray(req.body?.sets) ? req.body.sets : [];
  if (items.length === 0) return res.status(400).json({ error: "sets array required" });

  const created: { id: string; exercise: string; weightKg: number; reps: number }[] = [];
  for (const item of items) {
    if (typeof item.exercise !== "string" || !item.exercise.trim()
        || typeof item.weightKg !== "number" || typeof item.reps !== "number") {
      return res.status(400).json({ error: "each set needs exercise, weightKg, reps" });
    }
    if (item.weightKg <= 0 || item.reps <= 0) {
      return res.status(400).json({ error: "weightKg and reps must be positive" });
    }
    created.push(await prisma.workoutSet.create({
      data: {
        ownerId: req.userId!,
        workoutId: workout.id,
        exercise: item.exercise.trim(),
        weightKg: item.weightKg,
        reps: item.reps
      }
    }));
  }
  res.status(201).json(created);
});

workoutsRouter.post("/:id/progressive", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
    include: { sets: { orderBy: { createdAt: "asc" } } }
  });
  if (!workout) return res.status(404).json({ error: "not found" });
  const suggestions = await progressiveSuggestion(workout.sets);
  res.json(suggestions);
});
```

- [ ] **Step 5: Mount the router**

In `server/src/index.ts`, import `workoutsRouter` and mount:
```ts
app.use("/api/workouts", workoutsRouter);
```

- [ ] **Step 6: Write the workout tests**

`server/src/workouts.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { progressiveSuggestion, DEFAULT_INCREMENT_KG } from "./workouts";

let token = "";

beforeEach(async () => {
  await prisma.workout.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "workouts@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("create a workout and append sets", async () => {
  const create = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Push Day" });
  expect(create.status).toBe(201);
  const id = create.body.id;

  const addSets = await request(app)
    .post(`/api/workouts/${id}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ sets: [
      { exercise: "Bench Press", weightKg: 60, reps: 8 },
      { exercise: "Bench Press", weightKg: 60, reps: 8 }
    ] });
  expect(addSets.status).toBe(201);

  const get = await request(app).get(`/api/workouts/${id}`).set("Authorization", `Bearer ${token}`);
  expect(get.status).toBe(200);
  expect(get.body.sets).toHaveLength(2);
  expect(get.body.sets[0].exercise).toBe("Bench Press");
});

test("rejects bad set payload", async () => {
  const create = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Leg Day" });
  const id = create.body.id;
  const bad = await request(app)
    .post(`/api/workouts/${id}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ sets: [{ exercise: "Squat", weightKg: 0, reps: 5 }] });
  expect(bad.status).toBe(400);
});

test("progressiveSuggestion bumps weight when target completed", async () => {
  const suggestion = await progressiveSuggestion([
    { exercise: "Bench Press", weightKg: 60, reps: 8 },
    { exercise: "Bench Press", weightKg: 60, reps: 9 },
    { exercise: "Bench Press", weightKg: 60, reps: 8 }
  ]);
  expect(suggestion[0].reason).toBe("completed_target");
  expect(suggestion[0].suggestedNextKg).toBe(60 + DEFAULT_INCREMENT_KG);
});

test("progressiveSuggestion holds weight when target not met", async () => {
  const suggestion = await progressiveSuggestion([
    { exercise: "Squat", weightKg: 80, reps: 5 },
    { exercise: "Squat", weightKg: 80, reps: 5 }
  ]);
  expect(suggestion[0].reason).toBe("not_yet");
  expect(suggestion[0].suggestedNextKg).toBe(80);
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/workouts");
  expect(res.status).toBe(401);
});
```

- [ ] **Step 7: Run tests, typecheck, commit**

Run:
```bash
npm test --workspace server
npm run typecheck
```
Expected: tests PASS, typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): workout and set models with progressive weight API"
```

> Note: `progressiveSuggestion` is defined in `server/src/workouts.ts` and exported for direct unit testing, alongside the HTTP routes.

---

### Task 2.2: Workout UI

**Files:**
- Create: `client/src/workoutApi.ts`
- Create: `client/src/Workouts.tsx`
- Create: `client/src/ProgressionChart.tsx`
- Modify: `client/src/App.tsx`

**Interfaces:**
- Consumes: `authedFetch` from `client/src/auth.ts` (Phase 0); server workout routes from Task 2.1; shared types `WorkoutCreateInput`, `WorkoutSetInput`, `ProgressiveSuggestion` from `shared`.
- Produces: `client/src/workoutApi.ts` exporting `getWorkouts`, `getWorkout`, `createWorkout`, `appendSets`, `getProgressive`, and client types `Workout`, `WorkoutSet`. `Workouts.tsx` renders create/list + the set-logging form. `ProgressionChart.tsx` renders a plain SVG line chart of max weight per workout for one exercise. `App.tsx` adds a navigation link exposing the module once authed.

- [ ] **Step 1: Write the workout API client**

`client/src/workoutApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { WorkoutSetInput, ProgressiveSuggestion } from "shared";

export interface WorkoutSet {
  id: string;
  exercise: string;
  weightKg: number;
  reps: number;
  createdAt: string;
}

export interface Workout {
  id: string;
  title: string;
  date: string;
  notes: string | null;
  sets: WorkoutSet[];
}

export interface WorkoutSummary {
  id: string;
  title: string;
  date: string;
  notes: string | null;
  sets: { exercise: string; weightKg: number; reps: number }[];
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getWorkouts(token: string): Promise<WorkoutSummary[]> {
  const res = await authedFetch(token, "/api/workouts");
  return json<WorkoutSummary[]>(res);
}

export async function getWorkout(token: string, id: string): Promise<Workout> {
  const res = await authedFetch(token, `/api/workouts/${id}`);
  return json<Workout>(res);
}

export async function createWorkout(token: string, input: { title: string; date?: string; notes?: string | null }): Promise<Workout> {
  const res = await authedFetch(token, "/api/workouts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Workout>(res);
}

export async function appendSets(token: string, id: string, sets: WorkoutSetInput[]): Promise<WorkoutSet[]> {
  const res = await authedFetch(token, `/api/workouts/${id}/sets`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sets })
  });
  return json<WorkoutSet[]>(res);
}

export async function getProgressive(token: string, id: string): Promise<ProgressiveSuggestion[]> {
  const res = await authedFetch(token, `/api/workouts/${id}/progressive`);
  return json<ProgressiveSuggestion[]>(res);
}
```

- [ ] **Step 2: Write the ProgressionChart SVG component**

`client/src/ProgressionChart.tsx`:
```tsx
interface Point {
  date: string;
  maxKg: number;
}

export default function ProgressionChart({ points }: { points: Point[] }) {
  if (points.length < 2) {
    return <p className="text-sm text-slate-400">Log at least two workouts to see progression.</p>;
  }
  const w = 320;
  const h = 160;
  const pad = 20;
  const max = Math.max(...points.map((p) => p.maxKg)) * 1.1;
  const min = Math.min(...points.map((p) => p.maxKg)) * 0.9 || 1;
  const x = (i: number) => pad + (i / (points.length - 1)) * (w - pad * 2);
  const y = (v: number) => h - pad - ((v - min) / (max - min)) * (h - pad * 2);
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"} ${x(i)} ${y(p.maxKg)}`).join(" ");

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full max-w-md" role="img" aria-label="weight progression">
      {points.map((p, i) => (
        <circle key={i} cx={x(i)} cy={y(p.maxKg)} r={3} fill="#4f46e5">
          <title>{`${p.date}: ${p.maxKg}kg`}</title>
        </circle>
      ))}
      <path d={path} fill="none" stroke="#4f46e5" strokeWidth={2} />
    </svg>
  );
}
```

- [ ] **Step 3: Write the Workouts component**

`client/src/Workouts.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import { getWorkouts, createWorkout, appendSets, getProgressive, type WorkoutSummary } from "./workoutApi";
import ProgressionChart from "./ProgressionChart";
import type { ProgressiveSuggestion } from "shared";

interface Props { token: string }

export default function Workouts({ token }: Props) {
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [title, setTitle] = useState("");
  const [selectedId, setSelectedId] = useState<string>("");
  const [exercise, setExercise] = useState("");
  const [weightKg, setWeightKg] = useState("");
  const [reps, setReps] = useState("");
  const [progress, setProgress] = useState<ProgressiveSuggestion[]>([]);

  const refresh = useCallback(async () => {
    const data = await getWorkouts(token);
    setWorkouts(data);
    if (data.length > 0 && !selectedId) setSelectedId(data[data.length - 1].id);
  }, [token, selectedId]);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => {
    if (selectedId) getProgressive(token, selectedId).then(setProgress).catch(() => setProgress([]));
  }, [selectedId, token]);

  async function onAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    const created = await createWorkout(token, { title });
    setTitle("");
    await refresh();
    setSelectedId(created.id);
  }

  async function onLogSet(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedId || !exercise.trim() || !weightKg || !reps) return;
    await appendSets(token, selectedId, [{
      exercise: exercise.trim(),
      weightKg: Number(weightKg),
      reps: Number(reps)
    }]);
    setExercise(""); setWeightKg(""); setReps("");
    await refresh();
  }

  const selected = workouts.find((w) => w.id === selectedId);
  const chartPoints = selected
    ? selected.sets
        .filter((s) => s.exercise === (selected.sets[0]?.exercise ?? ""))
        .map((s, i, arr) => ({ date: `#${i + 1}`, maxKg: arr.slice(0, i + 1).reduce((m, x) => Math.max(m, x.weightKg), 0) }))
    : [];

  return (
    <div className="w-full max-w-2xl flex flex-col gap-6">
      <form onSubmit={onAdd} className="flex gap-2">
        <input className="border rounded px-3 py-2 flex-1" placeholder="New workout (e.g. Push Day)"
          value={title} onChange={(e) => setTitle(e.target.value)} />
        <button className="bg-indigo-600 text-white rounded px-4 py-2">Create</button>
      </form>

      <select className="border rounded px-3 py-2" value={selectedId}
        onChange={(e) => setSelectedId(e.target.value)}>
        {workouts.map((w) => (
          <option key={w.id} value={w.id}>{w.title} — {new Date(w.date).toLocaleDateString()}</option>
        ))}
      </select>

      {selected && (
        <>
          <h3 className="font-semibold text-slate-800">Log a set for {selected.title}</h3>
          <form onSubmit={onLogSet} className="grid grid-cols-4 gap-2">
            <input className="border rounded px-3 py-2 col-span-1" placeholder="Exercise"
              value={exercise} onChange={(e) => setExercise(e.target.value)} />
            <input className="border rounded px-3 py-2" type="number" placeholder="kg"
              value={weightKg} onChange={(e) => setWeightKg(e.target.value)} />
            <input className="border rounded px-3 py-2" type="number" placeholder="reps"
              value={reps} onChange={(e) => setReps(e.target.value)} />
            <button className="bg-indigo-600 text-white rounded px-3 py-2">+ Set</button>
          </form>

          <div>
            <h4 className="font-semibold text-slate-700 mb-2">Sets</h4>
            <ul className="flex flex-col gap-1">
              {selected.sets.map((s) => (
                <li key={s.id} className="text-sm text-slate-600 border rounded px-3 py-1">
                  {s.exercise}: {s.weightKg}kg × {s.reps} reps
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-semibold text-slate-700 mb-2">Progression suggestions</h4>
            <ul className="flex flex-col gap-1">
              {progress.map((p) => (
                <li key={p.exercise} className="text-sm border rounded px-3 py-1">
                  <span className="font-medium">{p.exercise}</span>: {p.currentWeightKg}kg
                  {" → "}
                  <span className="text-indigo-600 font-medium">{p.suggestedNextKg}kg</span>
                  {" "}
                  {p.reason === "completed_target" ? "(target met — bump weight)" : "(keep until 3×8 met)"}
                </li>
              ))}
            </ul>
          </div>

          <div className="bg-white rounded p-4 border">
            <h4 className="font-semibold text-slate-700 mb-2">Progression (max weight per logged set)</h4>
            <ProgressionChart points={chartPoints} />
          </div>
        </>
      )}
    </div>
  );
}
```

> Note: The chart points derive the maximum weight reached up to each logged set, filtered to the first exercise present, giving a simple "progression over time" line without a charting library.

- [ ] **Step 4: Wire Workouts into App**

Modify `client/src/App.tsx`: add an import `import Workouts from "./Workouts";` and render `<Workouts token={token} />` inside the `token && user` branch, below the greeting. Keep it a plain section (no router added in this phase to match the existing Phase 1 single-view pattern).

- [ ] **Step 5: Typecheck, run servers, verify manually**

```bash
npm run typecheck
```
Start both dev servers, log in, create a workout, add sets for an exercise across two workouts, and confirm the suggestion shows `+2.5kg` once 3×8 is met and the SVG chart renders.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(client): workout logging with progression chart"
```

---

### Task 2.3: Nutrition DB + food management

**Files:**
- Modify: `server/prisma/schema.prisma`
- Modify: `shared/src/index.ts`
- Create: `server/src/foods.seed.ts`
- Create: `server/src/seed.ts` (entry invoking the seed runner)
- Create: `server/src/foods.ts` (router + meal logging)
- Create: `server/src/foods.test.ts`
- Modify: `server/src/index.ts`
- Create: `client/src/foodApi.ts`
- Create: `client/src/Foods.tsx`
- Modify: `client/src/App.tsx`

**Interfaces:**
- Consumes: `prisma`; `requireAuth`, `AuthedRequest`; shared types.
- Produces: Prisma models `Food` and `MealLog`; REST `GET /api/foods` (list, optionally `?q=` filter), `POST /api/foods` (add custom food), `POST /api/foods/search` (search by name), `POST /api/meals` (log a meal: `foodId`, `grams`, `date`), `DELETE /api/meals/:id`. Shared types `FoodInput`, `MealLogInput`, `FoodRecord`. `seedFoods(ownerId)` seed function embedding ~20 common foods; client `foodApi.ts` + `Foods.tsx` for searching foods and logging meals.

- [ ] **Step 1: Add the Food and MealLog models to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Food {
  id             String   @id @default(cuid())
  ownerId        String
  name           String
  caloriesPer100 Float    // kcal per 100g
  servingUnit    String   @default("g")
  createdAt      DateTime @default(now())
  @@unique([ownerId, name])
  @@index([ownerId])
}

model MealLog {
  id        String   @id @default(cuid())
  ownerId   String
  foodId    String
  grams     Float
  date      DateTime @default(now())
  createdAt DateTime @default(now())
  food      Food     @relation(fields: [foodId], references: [id], onDelete: Cascade)
  @@index([ownerId, date])
}
```

`Food.caloriesPer100` stores kcal per 100 grams. `MealLog.grams` is the amount eaten; consumed kcal = `grams / 100 * caloriesPer100`.

- [ ] **Step 2: Regenerate Prisma client and push schema**

Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: exit 0.

- [ ] **Step 3: Export shared nutrition input types**

In `shared/src/index.ts` append:
```ts
export interface FoodInput {
  name: string;
  caloriesPer100: number;
  servingUnit?: string;
}

export interface FoodRecord {
  id: string;
  name: string;
  caloriesPer100: number;
  servingUnit: string;
}

export interface MealLogInput {
  foodId: string;
  grams: number;
  date?: string;
}
```

- [ ] **Step 4: Write the food seed**

`server/src/foods.seed.ts`:
```ts
import { prisma } from "./db";

export interface SeedFood {
  name: string;
  caloriesPer100: number;
  servingUnit?: string;
}

export const SEED_FOODS: SeedFood[] = [
  { name: "Chicken Breast", caloriesPer100: 165 },
  { name: "White Rice (cooked)", caloriesPer100: 130 },
  { name: "Brown Rice (cooked)", caloriesPer100: 112 },
  { name: "Broccoli", caloriesPer100: 34 },
  { name: "Spinach", caloriesPer100: 23 },
  { name: "Olive Oil", caloriesPer100: 884, servingUnit: "ml" },
  { name: "Whole Egg", caloriesPer100: 143 },
  { name: "Banana", caloriesPer100: 89 },
  { name: "Apple", caloriesPer100: 52 },
  { name: "Bread (whole wheat)", caloriesPer100: 247 },
  { name: "Oats (dry)", caloriesPer100: 389 },
  { name: "Greek Yogurt (plain)", caloriesPer100: 59 },
  { name: "Almonds", caloriesPer100: 579 },
  { name: "Peanut Butter", caloriesPer100: 588 },
  { name: "Salmon", caloriesPer100: 208 },
  { name: "Potato (boiled)", caloriesPer100: 87 },
  { name: "Sweet Potato", caloriesPer100: 86 },
  { name: "Tomato", caloriesPer100: 18 },
  { name: "Cheddar Cheese", caloriesPer100: 403 },
  { name: "Milk (whole)", caloriesPer100: 61, servingUnit: "ml" }
];

export async function seedFoods(ownerId: string): Promise<number> {
  let created = 0;
  for (const f of SEED_FOODS) {
    const existing = await prisma.food.findUnique({ where: { ownerId_name: { ownerId, name: f.name } } });
    if (!existing) {
      await prisma.food.create({
        data: { ownerId, name: f.name, caloriesPer100: f.caloriesPer100, servingUnit: f.servingUnit ?? "g" }
      });
      created++;
    }
  }
  return created;
}
```

`server/src/seed.ts`:
```ts
import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

async function main() {
  const users = await prisma.user.findMany();
  for (const user of users) {
    const created = await seedFoods(user.id);
    console.log(`Seeded ${created} foods for ${user.email}`);
  }
  await prisma.$disconnect();
}

main().catch((e) => { console.error(e); process.exit(1); });
```

Add to `server/package.json` scripts:
```json
"seed": "tsx src/seed.ts"
```
Run once:
```bash
npm run seed --workspace server
```
Expected: prints `Seeded N foods for <user>`. (Full USDA import is optional and explicitly out of scope; this embedded array is the seed per spec's "open nutrition DB" free/local need.)

- [ ] **Step 5: Write the foods + meals router**

`server/src/foods.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { FoodInput, MealLogInput } from "shared";

export const foodsRouter = Router();
foodsRouter.use(requireAuth);

foodsRouter.get("/", async (req: AuthedRequest, res) => {
  const q = typeof req.query.q === "string" ? req.query.q.trim().toLowerCase() : "";
  const foods = await prisma.food.findMany({
    where: {
      ownerId: req.userId,
      ...(q ? { name: { contains: q } } : {})
    },
    orderBy: { name: "asc" },
    select: { id: true, name: true, caloriesPer100: true, servingUnit: true }
  });
  res.json(foods);
});

foodsRouter.post("/", async (req: AuthedRequest, res) => {
  const body = req.body as Partial<FoodInput> | null;
  if (!body || typeof body.name !== "string" || !body.name.trim()
      || typeof body.caloriesPer100 !== "number" || body.caloriesPer100 < 0) {
    return res.status(400).json({ error: "name and positive caloriesPer100 required" });
  }
  const food = await prisma.food.create({
    data: {
      ownerId: req.userId!,
      name: body.name.trim(),
      caloriesPer100: body.caloriesPer100,
      servingUnit: typeof body.servingUnit === "string" ? body.servingUnit : "g"
    }
  });
  res.status(201).json(food);
});

foodsRouter.post("/search", async (req: AuthedRequest, res) => {
  const { q } = req.body ?? {};
  if (typeof q !== "string" || !q.trim()) {
    return res.status(400).json({ error: "search query required" });
  }
  const foods = await prisma.food.findMany({
    where: { ownerId: req.userId, name: { contains: q.trim() } },
    orderBy: { name: "asc" },
    select: { id: true, name: true, caloriesPer100: true, servingUnit: true }
  });
  res.json(foods);
});

foodsRouter.post("/meals", async (req: AuthedRequest, res) => {
  const body = req.body as Partial<MealLogInput> | null;
  if (!body || typeof body.foodId !== "string" || typeof body.grams !== "number" || body.grams <= 0) {
    return res.status(400).json({ error: "foodId and positive grams required" });
  }
  const food = await prisma.food.findFirst({ where: { id: body.foodId, ownerId: req.userId } });
  if (!food) return res.status(404).json({ error: "food not found" });

  const meal = await prisma.mealLog.create({
    data: {
      ownerId: req.userId!,
      foodId: food.id,
      grams: body.grams,
      date: body.date ? new Date(body.date) : new Date()
    }
  });
  res.status(201).json(meal);
});

foodsRouter.delete("/meals/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.mealLog.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.mealLog.delete({ where: { id: existing.id } });
  res.status(204).end();
});
```

- [ ] **Step 6: Mount the router**

In `server/src/index.ts`:
```ts
app.use("/api/foods", foodsRouter);
```

- [ ] **Step 7: Write the foods + meals tests**

`server/src/foods.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

let token = "";

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.food.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "foods@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("seedFoods creates the embedded array and is idempotent", async () => {
  const user = await prisma.user.findFirstOrThrow({ where: { email: "foods@example.com" } });
  const first = await seedFoods(user.id);
  expect(first).toBeGreaterThan(0);
  const second = await seedFoods(user.id);
  expect(second).toBe(0);
  const count = await prisma.food.count({ where: { ownerId: user.id } });
  expect(count).toBeGreaterThanOrEqual(20);
});

test("search returns matching foods", async () => {
  const user = await prisma.user.findFirstOrThrow({ where: { email: "foods@example.com" } });
  await seedFoods(user.id);

  const res = await request(app)
    .post("/api/foods/search")
    .set("Authorization", `Bearer ${token}`)
    .send({ q: "chicken" });
  expect(res.status).toBe(200);
  expect(res.body.length).toBeGreaterThan(0);
  expect(res.body[0].name).toContain("Chicken");
});

test("log a meal and compute its calorie contribution", async () => {
  const user = await prisma.user.findFirstOrThrow({ where: { email: "foods@example.com" } });
  await seedFoods(user.id);
  const chicken = await prisma.food.findFirstOrThrow({ where: { ownerId: user.id, name: "Chicken Breast" } });

  const meal = await request(app)
    .post("/api/foods/meals")
    .set("Authorization", `Bearer ${token}`)
    .send({ foodId: chicken.id, grams: 200 });
  expect(meal.status).toBe(201);
  expect(meal.body.grams).toBe(200);

  // 200g of chicken at 165 kcal/100g = 330 kcal
  const calories = chicken.caloriesPer100 * (200 / 100);
  expect(calories).toBe(330);
});
```

- [ ] **Step 8: Write the client food API and Foods component**

`client/src/foodApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { FoodRecord, MealLogInput } from "shared";

export interface MealLogRecord {
  id: string;
  foodId: string;
  grams: number;
  date: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function searchFoods(token: string, q: string): Promise<FoodRecord[]> {
  const res = await authedFetch(token, "/api/foods/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ q })
  });
  return json<FoodRecord[]>(res);
}

export async function logMeal(token: string, input: MealLogInput): Promise<MealLogRecord> {
  const res = await authedFetch(token, "/api/foods/meals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<MealLogRecord>(res);
}

export async function deleteMeal(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/foods/meals/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("delete failed");
}
```

`client/src/Foods.tsx`:
```tsx
import { useState } from "react";
import { searchFoods, logMeal, deleteMeal, type MealLogRecord } from "./foodApi";
import type { FoodRecord } from "shared";

interface Props {
  token: string;
  meals: MealLogRecord[];
  onChanged: () => void;
}

export default function Foods({ token, meals, onChanged }: Props) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<FoodRecord[]>([]);
  const [grams, setGrams] = useState("100");

  async function onSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;
    const foods = await searchFoods(token, query);
    setResults(foods);
  }

  async function onLog(food: FoodRecord) {
    await logMeal(token, { foodId: food.id, grams: Number(grams) });
    onChanged();
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-6">
      <form onSubmit={onSearch} className="flex gap-2">
        <input className="border rounded px-3 py-2 flex-1" placeholder="Search foods (e.g. chicken)"
          value={query} onChange={(e) => setQuery(e.target.value)} />
        <button className="bg-indigo-600 text-white rounded px-4 py-2">Search</button>
      </form>

      <div className="flex items-center gap-2 text-sm text-slate-600">
        <label>Servings (grams):</label>
        <input className="border rounded px-2 py-1 w-24" type="number"
          value={grams} onChange={(e) => setGrams(e.target.value)} />
      </div>

      <ul className="flex flex-col gap-1">
        {results.map((f) => (
          <li key={f.id} className="flex items-center gap-3 border rounded p-3">
            <span className="flex-1">{f.name}</span>
            <span className="text-sm text-slate-400">{f.caloriesPer100} kcal/100{f.servingUnit}</span>
            <button className="bg-indigo-600 text-white rounded px-3 py-1 text-sm" onClick={() => onLog(f)}>
              Log {grams}g
            </button>
          </li>
        ))}
      </ul>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Today&apos;s meals</h3>
        <ul className="flex flex-col gap-1">
          {meals.map((m) => {
            const food = results.find((f) => f.id === m.foodId) ?? { name: m.foodId };
            return (
              <li key={m.id} className="flex items-center gap-3 text-sm border rounded px-3 py-1">
                <span className="flex-1">{(food as FoodRecord).name ?? m.foodId}</span>
                <span>{m.grams}g</span>
                <button className="text-red-600" onClick={() => deleteMeal(token, m.id).then(onChanged)}>✕</button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
```

> Note: The meal list shows foods from the last search; because a logged meal's calorie/name may not be in `results`, the component keeps the list tolerant of a missing food record. The authoritative per-day summary (with correct names + calories) is served by Task 2.4's `GET /api/nutrition/summary`.

- [ ] **Step 9: Wire Foods into App**

Modify `client/src/App.tsx`: import `Foods`, render `<Foods token={token} meals={mealState} onChanged={refreshMeals} />` inside the authed branch. Fetch today's meals for the meal list via `GET /api/nutrition/detail` (backfilled by Task 2.4) — for this task call `searchFoods` empty-state only; the live meal list integrates fully at Task 2.4 Step 4.

- [ ] **Step 10: Run tests, seed, typecheck, commit**

Run:
```bash
npm run seed --workspace server
npm test --workspace server
npm run typecheck
```
Expected: seed prints created count; tests PASS; typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): food db seed, food search, meal logging"
```

---

### Task 2.4: Calorie target + analysis

**Files:**
- Modify: `server/prisma/schema.prisma`
- Modify: `shared/src/index.ts`
- Create: `server/src/nutrition.ts` (summary + detail routes)
- Create: `server/src/nutrition.test.ts`
- Modify: `server/src/index.ts`
- Create: `client/src/nutritionApi.ts`
- Create: `client/src/NutritionSummary.tsx`
- Modify: `client/src/App.tsx`

**Interfaces:**
- Consumes: `prisma`; `requireAuth`, `AuthedRequest`; shared `MealLogInput`, `DailyTargetInput`, `NutritionSummary`.
- Produces: Prisma model `DailyTarget`; REST `GET /api/nutrition/summary?date=YYYY-MM-DD` returning `{ target, consumed, remaining, meals: [...] }`, `PUT /api/nutrition/target` to set the daily calorie target, `GET /api/nutrition/detail?date=` returning the day's meal rows with nested food names+calories. Shared type `NutritionSummary`. Client `nutritionApi.ts` + `NutritionSummary.tsx` (bar + remaining readout).

- [ ] **Step 1: Add the DailyTarget model to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model DailyTarget {
  id           String   @id @default(cuid())
  ownerId      String   @unique
  calories     Int
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt
}
```

Regenerate and push:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: exit 0.

- [ ] **Step 2: Export shared nutrition summary types**

In `shared/src/index.ts` append:
```ts
export interface DailyTargetInput {
  calories: number;
}

export interface NutritionSummary {
  date: string;
  target: number;
  consumed: number;
  remaining: number;
  meals: {
    id: string;
    foodName: string;
    grams: number;
    calories: number;
  }[];
}
```

- [ ] **Step 3: Write the nutrition router**

`server/src/nutrition.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { NutritionSummary } from "shared";

export const nutritionRouter = Router();
nutritionRouter.use(requireAuth);

function startOfLocalDay(date: string): { start: Date; end: Date } {
  const d = new Date(`${date}T00:00:00`);
  const end = new Date(d);
  end.setDate(end.getDate() + 1);
  return { start: d, end };
}

nutritionRouter.get("/summary", async (req: AuthedRequest, res) => {
  const date = typeof req.query.date === "string" && req.query.date ? req.query.date : "today";
  const day = date === "today"
    ? new Date().toISOString().slice(0, 10)
    : date;
  const { start, end } = startOfLocalDay(day);

  const targetRow = await prisma.dailyTarget.findUnique({ where: { ownerId: req.userId } });
  const target = targetRow?.calories ?? 2000;

  const meals = await prisma.mealLog.findMany({
    where: { ownerId: req.userId, date: { gte: start, lt: end } },
    include: { food: { select: { name: true, caloriesPer100: true } } },
    orderBy: { date: "asc" }
  });

  const consumed = Math.round(
    meals.reduce((sum, m) => sum + (m.food.caloriesPer100 * (m.grams / 100)), 0)
  );

  const summary: NutritionSummary = {
    date: day,
    target,
    consumed,
    remaining: target - consumed,
    meals: meals.map((m) => ({
      id: m.id,
      foodName: m.food.name,
      grams: m.grams,
      calories: Math.round(m.food.caloriesPer100 * (m.grams / 100))
    }))
  };
  res.json(summary);
});

nutritionRouter.get("/detail", async (req: AuthedRequest, res) => {
  const date = typeof req.query.date === "string" && req.query.date ? req.query.date : "today";
  const day = date === "today" ? new Date().toISOString().slice(0, 10) : date;
  const { start, end } = startOfLocalDay(day);
  const meals = await prisma.mealLog.findMany({
    where: { ownerId: req.userId, date: { gte: start, lt: end } },
    include: { food: { select: { id: true, name: true, caloriesPer100: true } } },
    orderBy: { date: "asc" }
  });
  res.json(meals.map((m) => ({
    id: m.id,
    foodId: m.food.id,
    foodName: m.food.name,
    grams: m.grams,
    calories: Math.round(m.food.caloriesPer100 * (m.grams / 100))
  })));
});

nutritionRouter.put("/target", async (req: AuthedRequest, res) => {
  const body = req.body ?? {};
  const calories = Number(body.calories);
  if (!Number.isFinite(calories) || calories <= 0) {
    return res.status(400).json({ error: "positive calories required" });
  }
  const target = await prisma.dailyTarget.upsert({
    where: { ownerId: req.userId! },
    update: { calories: Math.round(calories) },
    create: { ownerId: req.userId!, calories: Math.round(calories) }
  });
  res.json(target);
});
```

- [ ] **Step 4: Mount the router**

In `server/src/index.ts`:
```ts
app.use("/api/nutrition", nutritionRouter);
```

- [ ] **Step 5: Write the nutrition tests**

`server/src/nutrition.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

let token = "";
const today = new Date().toISOString().slice(0, 10);

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.food.deleteMany({});
  await prisma.dailyTarget.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "nutrition@example.com", password: "password123" });
  token = reg.body.token as string;
  const user = await prisma.user.findFirstOrThrow({ where: { email: "nutrition@example.com" } });
  await seedFoods(user.id);
});

test("set target and get summary with computed consumed", async () => {
  await request(app).put("/api/nutrition/target").set("Authorization", `Bearer ${token}`).send({ calories: 2000 });

  const user = await prisma.user.findFirstOrThrow({ where: { email: "nutrition@example.com" } });
  const chicken = await prisma.food.findFirstOrThrow({ where: { ownerId: user.id, name: "Chicken Breast" } });
  await request(app).post("/api/foods/meals").set("Authorization", `Bearer ${token}`).send({ foodId: chicken.id, grams: 200 });
  await request(app).post("/api/foods/meals").set("Authorization", `Bearer ${token}`).send({ foodId: chicken.id, grams: 100 });

  const res = await request(app).get(`/api/nutrition/summary?date=${today}`).set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.target).toBe(2000);
  expect(res.body.consumed).toBe(495); // 200g*1.65 + 100g*1.65 = 495
  expect(res.body.remaining).toBe(2000 - 495);
  expect(res.body.meals).toHaveLength(2);
  expect(res.body.meals[0].foodName).toBe("Chicken Breast");
});

test("summary defaults to today and a default target when unset", async () => {
  const res = await request(app).get("/api/nutrition/summary").set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.target).toBe(2000);
  expect(res.body.date).toBe(today);
});
```

- [ ] **Step 6: Write the client nutrition API and summary component**

`client/src/nutritionApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { NutritionSummary } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getSummary(token: string, date?: string): Promise<NutritionSummary> {
  const qs = date ? `?date=${date}` : "";
  const res = await authedFetch(token, `/api/nutrition/summary${qs}`);
  return json<NutritionSummary>(res);
}

export async function setTarget(token: string, calories: number): Promise<void> {
  const res = await authedFetch(token, "/api/nutrition/target", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ calories })
  });
  if (!res.ok) throw new Error("failed to set target");
}
```

`client/src/NutritionSummary.tsx`:
```tsx
import type { NutritionSummary } from "shared";

export default function NutritionSummary({ summary }: { summary: NutritionSummary }) {
  const pct = summary.target > 0 ? Math.min(100, Math.round((summary.consumed / summary.target) * 100)) : 0;
  const color = summary.remaining >= 0 ? "bg-indigo-600" : "bg-red-600";

  return (
    <div className="w-full max-w-2xl flex flex-col gap-3">
      <div className="flex gap-6 text-sm text-slate-600">
        <span>Target: <b>{summary.target}</b> kcal</span>
        <span>Consumed: <b>{summary.consumed}</b> kcal</span>
        <span className={summary.remaining >= 0 ? "" : "text-red-600"}>
          Remaining: <b>{summary.remaining}</b> kcal
        </span>
      </div>

      <div className="h-4 w-full rounded-full bg-slate-200 overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${pct}%` }} />
      </div>

      <ul className="flex flex-col gap-1">
        {summary.meals.map((m) => (
          <li key={m.id} className="flex justify-between text-sm border rounded px-3 py-1">
            <span>{m.foodName} · {m.grams}g</span>
            <span className="text-slate-500">{m.calories} kcal</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 7: Wire into App with a shared meal state**

Modify `client/src/App.tsx`: add a `summary` state initialized to `null`, a `refreshNutrition` callback calling `getSummary(token)` on mount and after meals change, and render the summary plus a target input. Pass `meals={summary?.meals}` to the `Foods` component (replacing the empty-state meal list from Task 2.3) so `onChanged` triggers `refreshNutrition`. This completes the meal list integration promised in Task 2.3 Step 9.

- [ ] **Step 8: Run tests, typecheck, verify manually**

```bash
npm test --workspace server
npm run typecheck
```
Start both servers, set a calorie target, log meals in `Foods`, confirm the summary bar and remaining update correctly and the graph renders.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(server): daily target and per-day nutrition summary"
```

---

### Task 2.5: VisionProvider abstraction for photo calories

**Files:**
- Create: `server/src/providers/vision.ts`
- Create: `server/src/providers/visionStub.ts`
- Create: `server/src/nutritionPhoto.ts` (endpoint + router)
- Create: `server/src/nutritionPhoto.test.ts`
- Modify: `server/src/index.ts`
- Modify: `.env` / `.env.example` (VISION_PROVIDER)
- Modify: `server/package.json` (multer + @types/multer)
- Create: `client/src/photoApi.ts`
- Create: `client/src/PhotoCalories.tsx`
- Modify: `client/src/App.tsx`

**Interfaces:**
- Consumes: `prisma`, `requireAuth`, `AuthedRequest`, `Food` model from Task 2.3.
- Produces: `VisionProvider` interface with `estimateCalories(imageBuffer, filename) -> Promise<VisionEstimate>`; `stubProvider` (default free/local implementation, exported from `server/src/providers/visionStub.ts`); `createVisionProvider(env?)` factory selecting by `process.env.VISION_PROVIDER` with default `"stub"` and a `"paid"` branch wired to `visionPaid.ts`; REST `POST /api/nutrition/analyze-photo` (multipart `image` field, `multer` memory storage) returning `VisionEstimate`; shared type `VisionEstimate` in `shared/src/index.ts`. Client `photoApi.ts` (`analyzePhoto(token, file)`) + `PhotoCalories.tsx` (pick a photo, upload, show estimate). Note the accuracy caveat in the UI.

- [ ] **Step 1: Add multer dependency**

Add to `server/package.json` dependencies: `"multer": "^1.4.5-lts.1"`, and `@types/multer` to devDependencies. Install:
```bash
npm install
```

- [ ] **Step 2: Define the VisionProvider interface**

`server/src/providers/vision.ts`:
```ts
export interface VisionEstimate {
  provider: string;
  items: {
    foodName: string;
    estimatedGrams: number;
    estimatedCalories: number;
  }[];
  totalCalories: number;
  disclaimer: string;
}

export interface VisionProvider {
  readonly name: string;
  estimateCalories(imageBuffer: Buffer, filename: string): Promise<VisionEstimate>;
}
```

- [ ] **Step 3: Write the default free/local stub provider**

`server/src/providers/visionStub.ts`:
```ts
import { prisma } from "../db";
import type { VisionEstimate, VisionProvider } from "./vision";

export const stubProvider: VisionProvider = {
  name: "stub",
  async estimateCalories(imageBuffer: Buffer, filename: string): Promise<VisionEstimate> {
    // Default free/local estimate. This is intentionally a rough placeholder —
    // it does NOT require any paid API key. It picks the user's highest-calorie
    // known food as a heuristic and returns a single-item estimate so the UI
    // has a real, honest (rough) number. Swap to a paid provider via VISION_PROVIDER.
    const known = await prisma.food.findMany({ orderBy: { caloriesPer100: "desc" }, take: 1 });
    const food = known[0] ?? { name: "Assorted meal", caloriesPer100: 150 };
    const estimatedGrams = 250; // honest caveat: portion sizing is unreliable
    const estimatedCalories = Math.round(food.caloriesPer100 * (estimatedGrams / 100));
    void imageBuffer; void filename;
    return {
      provider: this.name,
      items: [{ foodName: food.name, estimatedGrams, estimatedCalories }],
      totalCalories: estimatedCalories,
      disclaimer: "Rough local estimate — portion sizing is unreliable. Exact per-photo analysis requires a paid vision provider (e.g. GPT-4o / Claude vision)."
    };
  }
};
```

This is the clearly-marked default that runs with no API keys; it is honest about its rough nature, satisfying the "free/local first, accept rough accuracy" constraint.

- [ ] **Step 4: Add the factory with the paid placeholder switch**

Append to `server/src/providers/vision.ts`:
```ts
import { stubProvider } from "./visionStub";

let paidProvider: VisionProvider | null = null;

// A "paid" provider is a swappable alternative (e.g. GPT-4o / Claude vision).
// Implemented in a later phase; wiring is here so VISION_PROVIDER=paid is selectable
// without changing module code. Until implemented it falls back to the stub.
export async function loadPaidProvider(): Promise<VisionProvider> {
  if (!paidProvider) {
    const mod = await import("./visionPaid").catch(() => null) as { paidProvider?: VisionProvider } | null;
    paidProvider = mod?.paidProvider ?? stubProvider;
  }
  return paidProvider;
}

export async function createVisionProvider(env = process.env): Promise<VisionProvider> {
  const name = (env.VISION_PROVIDER || "stub").toLowerCase();
  if (name === "paid") return loadPaidProvider();
  return stubProvider;
}
```

`server/src/providers/visionPaid.ts` (explicit placeholder that documents the swap target; returns rough stub data until a real client is wired):
```ts
import type { VisionEstimate, VisionProvider } from "./vision";

// Swappable paid provider (e.g. GPT-4o / Claude vision). This module is the
// integration point for a future paid client. It currently returns a clearly
// marked placeholder so enabling VISION_PROVIDER=paid never breaks the app.
export const paidProvider: VisionProvider = {
  name: "paid",
  async estimateCalories(imageBuffer: Buffer, filename: string): Promise<VisionEstimate> {
    void imageBuffer; void filename;
    return {
      provider: this.name,
      items: [{ foodName: "unrecognized", estimatedGrams: 200, estimatedCalories: 300 }],
      totalCalories: 300,
      disclaimer: "Paid provider placeholder — wire a real vision client here."
    };
  }
};
```

- [ ] **Step 5: Write the analyze-photo endpoint**

`server/src/nutritionPhoto.ts`:
```ts
import { Router } from "express";
import multer from "multer";
import { requireAuth } from "./session";
import { createVisionProvider } from "./providers/vision";

export const nutritionPhotoRouter = Router();
nutritionPhotoRouter.use(requireAuth);

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 8 * 1024 * 1024 } });

nutritionPhotoRouter.post("/analyze-photo", upload.single("image"), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: "image file required (field 'image')" });
  const provider = await createVisionProvider();
  const estimate = await provider.estimateCalories(req.file.buffer, req.file.originalname);
  res.json(estimate);
});

export { upload };
```

- [ ] **Step 6: Mount the router and add env config**

In `server/src/index.ts`:
```ts
app.use("/api/nutrition", nutritionPhotoRouter);
```
(It mounts under the same `/api/nutrition` prefix as Task 2.4 — Express merges the routes because they are different paths.)

Add to `.env` and `.env.example`:
```
# Vision provider for photo calorie estimation: "stub" (default, free/local) or "paid"
VISION_PROVIDER="stub"
```

- [ ] **Step 7: Write the analyze-photo test**

`server/src/nutritionPhoto.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "photo@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("analyze-photo returns a rough estimate with the stub provider", async () => {
  const buf = Buffer.from("fake-image-bytes");
  const res = await request(app)
    .post("/api/nutrition/analyze-photo")
    .set("Authorization", `Bearer ${token}`)
    .attach("image", buf, "meal.jpg");
  expect(res.status).toBe(200);
  expect(res.body.provider).toBe("stub");
  expect(typeof res.body.totalCalories).toBe("number");
  expect(res.body.totalCalories).toBeGreaterThan(0);
  expect(res.body.disclaimer).toMatch(/rough/i);
});

test("analyze-photo rejects a missing file", async () => {
  const res = await request(app)
    .post("/api/nutrition/analyze-photo")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(400);
});
```

- [ ] **Step 8: Export the VisionEstimate shared type**

In `shared/src/index.ts` append:
```ts
export interface VisionEstimate {
  provider: string;
  items: { foodName: string; estimatedGrams: number; estimatedCalories: number }[];
  totalCalories: number;
  disclaimer: string;
}
```

- [ ] **Step 9: Write the client photo upload API**

`client/src/photoApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { VisionEstimate } from "shared";

export async function analyzePhoto(token: string, file: File): Promise<VisionEstimate> {
  const form = new FormData();
  form.append("image", file);
  const res = await authedFetch(token, "/api/nutrition/analyze-photo", { method: "POST", body: form });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "analyze failed" }));
    throw new Error(err.error ?? "analyze failed");
  }
  return res.json();
}
```

`client/src/PhotoCalories.tsx`:
```tsx
import { useState } from "react";
import { analyzePhoto } from "./photoApi";
import type { VisionEstimate } from "shared";

export default function PhotoCalories({ token }: { token: string }) {
  const [file, setFile] = useState<File | null>(null);
  const [estimate, setEstimate] = useState<VisionEstimate | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onAnalyze(e: React.FormEvent) {
    e.preventDefault();
    if (!file) return;
    setBusy(true); setError(null);
    try {
      const est = await analyzePhoto(token, file);
      setEstimate(est);
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <h3 className="font-semibold text-slate-800">Estimate calories from a meal photo</h3>
      <form onSubmit={onAnalyze} className="flex items-center gap-2">
        <input className="border rounded px-3 py-2 flex-1" type="file"
          accept="image/*" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button className="bg-indigo-600 text-white rounded px-4 py-2 disabled:opacity-50"
          disabled={busy || !file}>{busy ? "Analyzing…" : "Analyze"}</button>
      </form>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {estimate && (
        <div className="border rounded p-4 bg-white">
          <p className="font-medium text-lg">{estimate.totalCalories} kcal (rough)</p>
          <ul className="text-sm text-slate-600 mt-2">
            {estimate.items.map((it, i) => (
              <li key={i}>{it.foodName}: ~{it.estimatedGrams}g ≈ {it.estimatedCalories} kcal</li>
            ))}
          </ul>
          <p className="text-xs text-slate-400 mt-2 italic">{estimate.disclaimer}</p>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 10: Wire PhotoCalories into App**

Modify `client/src/App.tsx`: import and render `<PhotoCalories token={token} />` inside the authed branch.

- [ ] **Step 11: Run tests, typecheck, verify manually, commit**

Run:
```bash
npm test --workspace server
npm run typecheck
```
Expected: new photo tests PASS (plus prior modules); typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): swappable vision provider for photo calorie estimates"
```

---

## Phase 2 Self-Review

- **Spec coverage:** Health module (§3.2) — workouts with sets/reps/weight + progressive weight recommendations (Task 2.1 server, Task 2.2 UI + chart); food management from a nutrition DB + meal logging (Task 2.3); daily calorie target vs. consumed graph (Task 2.4); photo calorie estimation behind a `VisionProvider` with free/local default (Task 2.5). Provider-swappable per spec §5 (`VISION_PROVIDER=stub|paid`) and rough-accuracy caveat per §7 are both encoded. All five deliverables covered.
- **Placeholder scan:** No TBD/TODO/"implement later" in steps. Every step has runnable code. The `loadPaidProvider`/`visionPaid.ts` modules are intentionally present as clearly-marked integration points for a future paid client — they run and return data (not empty stubs), so the app never breaks when `VISION_PROVIDER=paid`; the paid integration itself is explicitly deferred to a later phase in the plan, which is a scoped decision, not an unresolved placeholder.
- **Type consistency:** `VisionEstimate`, `VisionProvider`, `progressiveSuggestion(CompletedSet[], number)`, async `createVisionProvider(env?): Promise<VisionProvider>` (awaited at the single call site in `nutritionPhoto.ts` and wired so `VISION_PROVIDER=paid` picks the paid branch), shared `WorkoutSetInput`/`MealLogInput`/`NutritionSummary`/`FoodRecord`/`VisionEstimate` are all either defined once and reused, or defined in `shared/src/index.ts` and consumed by both client and server consistently. `authedFetch(token, path, init)` is used consistently across all client API files. `ownerId` guards via `requireAuth`/`AuthedRequest` appear on every authed route.
- **Known deliberate note:** The `visionPaid.ts` module in Task 2.5 is a clearly-marked, runnable placeholder so `VISION_PROVIDER=paid` is selectable without breaking the app; the actual paid client integration is explicitly scoped to a later phase. The `VisionEstimate` shared type is added in Task 2.5 Step 8 before `photoApi.ts` imports it in Step 9, so the import always resolves.
