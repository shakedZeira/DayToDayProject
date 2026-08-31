# Workout Tab Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Reshape the Workout tab from a bare "create a titled workout and append free-form sets" screen into a real workout tracker mirroring how commercial apps (Strong, Hevy, Jefit, Boostcamp, StrongLifts 5x5) work: an **exercise library**, **routines/templates**, **start-a-workout-from-a-routine**, fast set logging with **previous-performance prefill**, **set types** (working vs warm-up), **rest timer**, and **progressive-overload + PR/volume analytics** (Epley e1RM).

**Architecture:** Evolve the existing Prisma models (`Workout`, `WorkoutSet`) behind the `workoutsRouter` (JWT `requireAuth`). Add `Exercise`, `Routine`, `RoutineDay`, `RoutineExercise`, `WorkoutExercise`, and `PersonalRecord` models. Keep the existing single-user `ownerId` scoping on every user-owned row. Progressive overload reuses the existing `progressiveSuggestion` server logic but extends it into a persistent **target vs actual** model (`ExerciseProgression` per the research's "Torqe gap" — the one thing the best new apps add: persistent rep/weight goals shown next to actuals). Client (React PWA) gets a redesigned workouts tab with an active-session flow, exercise/routine pickers, and analytics.

> **Important (project constraint):** Prisma + SQLite does **NOT** support enums — all "enum-like" fields (muscle group, equipment, set type, force type, activity) are **String** fields (validated in the route). Single-user: every user-owned row has `ownerId`.

**Tech Stack:** Express, Prisma, SQLite, React, TypeScript, Tailwind, plain inline SVG for charts.

> **Cross-module hooks:**
> - **Weekly goals (`autoSource: "workout"`):** keep the existing behavior — a new workout (POST `/api/workouts`) inserts a `GoalEvent` if the user has a matching `WeeklyGoal`. Must NOT regress when adding routine-based creation.
> - **Routines are shared templates?** For a single-user app make routines **user-owned** (`ownerId`), not global. Exercises, however, are a **shared global library** (like the Food catalog is now global) — seed once, all users read it; custom exercises are user-owned.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md` (health module §3.2).

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; `npm run typecheck` (clean all workspaces) and `npm test --workspace server` must pass before any commit. `server/vitest.config.ts` has `fileParallelism:false` — do NOT remove it.
- Prisma SQLite: no enums — use String fields with route-side validation.
- Authed routes require a Bearer token via `requireAuth` from `server/src/session.ts` (`AuthedRequest` gives `req.userId`).
- The `schemas` use `npx prisma db push` (no migrations folder). A dev server may hold `dev.db`/DLL — additive changes are safe; destructive changes (dropping columns) need `db push --force-reset` or stopping the dev verver.
- Windows host (cmd.exe shell). No C: usage. No heredocs in shell.

---

## Current State (baseline, verified)

- `server/prisma/schema.prisma`: `Workout { id, ownerId, title, date, notes, createdAt, sets[] }` and `WorkoutSet { id, ownerId, workoutId, exercise, weightKg, reps, createdAt }` (each row = one logged set; `WorkoutSet.exercise` is a free-text string).
- `server/src/workouts.ts`: `workoutsRouter` with `GET /`, `POST /`, `GET /:id`, `POST /:id/sets`, `POST /:id/progressive`; exports `progressiveSuggestion(completedSets, incrementKg=DEFAULT_INCREMENT_KG=2.5)` (suggests +2.5kg when ≥3 sets all ≥8 reps) and `historyFor(ownerId)`. The `POST /` also fires the weekly-goal auto hook.
- `client/src/Workouts.tsx`: lists workouts in a `<select>`, one form to append a single set (`exercise` text, `weightKg`, `reps`) to the selected workout, shows `progressiveSuggestion` results and a simple `ProgressionChart`.
- Shared types in `shared/src/index.ts`: `WorkoutCreateInput`, `WorkoutSetInput`, `ProgressiveSuggestion`, `FoodInput/FoodRecord` (now with `nameHe`).
- Client is now a tabbed app (`App.tsx` has a `workouts` tab rendering `Workouts`).

### Gap vs. industry (from research)
- No exercise library (free-text exercise names → inconsistent, no categorization, can't track PRs per exercise reliably).
- No routines/templates (can't turn a "Push Day" plan into a recurring session).
- Sets are unstructured (no set type; warm-ups pollute volume/PR).
- No previous-performance prefill, rest timer, or target-vs-actual persistence.
- Analytics are minimal (one line chart); PRs / estimated 1RM / volume absent.

---

## Task W1: Exercise library (shared/global) + custom exercises

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/exercises.ts` (router)
- Create: `server/src/exercises.seed.ts`
- Modify: `server/src/seed.ts` + `server/package.json` (seed stays `tsx src/seed.ts`, idempotent)
- Create: `server/src/exercises.test.ts`
- Modify: `server/src/index.ts`
- Modify: `shared/src/index.ts`
- Create: `client/src/exerciseApi.ts`

**Interfaces:**
- Consumes: `prisma`, `requireAuth`, `AuthedRequest`, router pattern from `foods.ts`.
- Produces: `Exercise` model; REST `GET /api/exercises`, `POST /api/exercises` (custom user exercises), `GET /api/exercises/search?q=`. Shared type `ExerciseRecord { id, name, muscleGroup, equipment, isCompound, isCustom }`. Client `exerciseApi.ts` for searching/fetching.

- [ ] **Step 1: Add the Exercise model to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Exercise {
  id           String  @id @default(cuid())
  ownerId      String?            // null => shared global library; set => user custom
  name         String
  muscleGroup  String  @default("Other")   // Chest|Back|Shoulders|Arms|Legs|Core|Glutes|Other
  equipment    String  @default("Bodyweight") // Barbell|Dumbbell|Cable|Machine|Bodyweight|Kettlebell|Band|Other
  isCompound   Boolean @default(true)
  instructions String?
  createdAt    DateTime @default(now())

  @@unique([ownerId, name])
  @@index([ownerId])
  @@index([name])
}
```
`ownerId = null` marks shared/global seeded exercises (like the global Food catalog). Custom user exercises set `ownerId`.

- [ ] **Step 2: Regenerate Prisma client + push schema**

```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: exit 0; the new table added (additive).

- [ ] **Step 3: Shared type**

In `shared/src/index.ts` append:
```ts
export interface ExerciseRecord {
  id: string; name: string; muscleGroup: string; equipment: string;
  isCompound: boolean; isCustom: boolean;
}
export interface ExerciseCreateInput { name: string; muscleGroup?: string; equipment?: string; isCompound?: boolean; }
```

- [ ] **Step 4: `server/src/exercises.ts` router**

`exercisesRouter.use(requireAuth)`. Routes (all authed):
- `GET /api/exercises` — returns the shared library + the user's custom exercises. Shared = `ownerId: null`; custom = `ownerId: req.userId`. Optional `?q=` filters `name contains q` (case-insensitive).
- `GET /api/exercises/search` — same as `GET /?q=` (keep one canonical: just implement `GET /` with optional `?q`; add a `/search` alias if the client prefers a POST—decision: use `GET /?q=` only, simpler).
- `POST /api/exercises` — create a **custom** user exercise. Validate `name` (required non-empty), `muscleGroup`/`equipment` optional strings (validate against allowed sets via a constant), `isCompound` optional boolean default true. Set `ownerId: req.userId`. Return 201 `ExerciseRecord`.

Export `const EXERCISE_MUSCLE_GROUPS` and `const EXERCISE_EQUIPMENT` arrays for validation/reuse.

- [ ] **Step 5: Seed a shared exercise library**

`server/src/exercises.seed.ts` exports `EXERCISES: { name; muscleGroup; equipment; isCompound }[]` (~60–80 exercises) and `seedExercises()`:
- Insert each with `ownerId: null`, `where: { ownerId: null, name }` skip-if-exists (idempotent). Return created count.
- Cover the major patterns: Squat, Deadlift, Bench Press, Overhead Press, Barbell Row, Pull-Up, Lat Pulldown, Leg Press, Romanian Deadlift, Lunges, Bicep Curl, Tricep Pushdown, Lateral Raise, Calf Raise, Plank, etc. Categorize with correct muscleGroup/equipment/isCompound.

Update `server/src/seed.ts` to call `seedExercises()` once (alongside `seedFoods()`).

- [ ] **Step 6: Wire the router in `server/src/index.ts`**

`import { exercisesRouter } from "./exercises";` and `app.use("/api/exercises", exercisesRouter);`

- [ ] **Step 7: Tests `server/src/exercises.test.ts`**

Register a user; assert:
1. `GET /api/exercises` returns the seeded shared library (>40) even for a fresh user (proves global).
2. `GET /api/exercises?q=bench` returns Bench Press.
3. `POST /api/exercises` creates a custom exercise; it appears in `GET /api/exercises` for that user and has `isCustom: true, isCompound:false` when requested.
4. `POST /api/exercises` with blank name → 400.

- [ ] **Step 8: Client `client/src/exerciseApi.ts`**

`getExercises(token, q?)` and `createExercise(token, input)` via `authedFetch`.

---

## Task W2: Routines and routine days

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/routines.ts` (router)
- Create: `server/src/routines.test.ts`
- Modify: `server/src/index.ts`
- Modify: `shared/src/index.ts`
- Modify: `server/src/seed.ts` (seed default routine templates)
- Create: `client/src/routineApi.ts`
- Create: `client/src/Routines.tsx`

**Interfaces:**
- Consumes: `prisma`, `requireAuth`, `Exercise` model from Task W1.
- Produces: `Routine`, `RoutineDay`, `RoutineExercise` models; REST CRUD for routines/days/exercises; shared types `RoutineView`, `RoutineDayView`, `RoutineExerciseInput`. Client `routineApi.ts` + a `Routines` screen (create a routine, add named days, add exercises with target sets/reps from the library). Seeded starter templates (PPL, Full Body, Upper/Lower).

- [ ] **Step 1: Prisma models**

Append to `server/prisma/schema.prisma`:
```prisma
model Routine {
  id          String   @id @default(cuid())
  ownerId     String
  name        String
  description String?
  createdAt   DateTime @default(now())
  days        RoutineDay[]

  @@index([ownerId])
}

model RoutineDay {
  id        String  @id @default(cuid())
  ownerId   String
  name      String
  order     Int
  routineId String
  routine   Routine @relation(fields: [routineId], references: [id], onDelete: Cascade)
  exercises RoutineExercise[]

  @@index([ownerId])
}

model RoutineExercise {
  id            String      @id @default(cuid())
  ownerId       String
  order         Int
  targetSets    Int
  targetReps    Int
  targetWeight  Float?
  notes         String?
  routineDayId  String
  routineDay    RoutineDay   @relation(fields: [routineDayId], references: [id], onDelete: Cascade)
  exerciseId    String
  exercise      Exercise     @relation(fields: [exerciseId], references: [id])

  @@index([ownerId])
}
```

- [ ] **Step 2: Regenerate + push** (same commands as W1).

- [ ] **Step 3: Shared types**

Append to `shared/src/index.ts`:
```ts
export interface RoutineExerciseInput {
  order: number; targetSets: number; targetReps: number; targetWeight?: number | null;
  notes?: string | null; exerciseId: string;
}
export interface RoutineDayInput { name: string; order?: number; exercises?: RoutineExerciseInput[]; }
export interface RoutineCreateInput { name: string; description?: string | null; days?: RoutineDayInput[]; }
export interface RoutineDayView {
  id: string; name: string; order: number;
  exercises: (RoutineExerciseInput & { id: string; exercise: ExerciseRecord })[];
}
export interface RoutineView {
  id: string; name: string; description: string | null;
  days: RoutineDayView[];
}
```

- [ ] **Step 4: `server/src/routines.ts` router**

`routinesRouter.use(requireAuth)`. Routes (all scoped to `req.userId`):
- `GET /api/routines` — list user's routines (with days+exercises+exercise info).
- `POST /api/routines` — create a routine with optional nested days/exercises (validate: name required; targetSets/targetReps positive ints; exerciseId references an existing Exercise accessible to user — must exist in shared library OR be owned by user).
- `GET /api/routines/:id`, `PUT /api/routines/:id` (replace name/description/days — implement as delete-and-recreate days for simplicity, single-user), `DELETE /api/routines/:id`.

Keep it pragmatic: `POST` accepts full nested structure and `PUT` replaces the whole routine (delete days, recreate). Validate all nested inputs.

- [ ] **Step 5: Seed default routines**

In `server/src/seed.ts` (or a `routines.seed.ts`): for each user, if they have zero routines, seed a few starter templates referencing the shared exercise library by name (resolve exerciseId from the shared `ownerId: null` library): e.g. **Full Body A**, **Push/Pull/Legs (Push A)**, **Upper/Lower (Upper A)** with realistic target sets/reps. Mark clearly as a convenience starter.

- [ ] **Step 6: Mount router** in `server/src/index.ts`.

- [ ] **Step 7: Tests `server/src/routines.test.ts`**

1. Create a routine with nested day + 2 exercises (using shared library exercise ids) → 201, returns days/exercises.
2. `GET /api/routines` lists it for that user (and isolates per user).
3. `PUT` replacement updates days.
4. `DELETE` removes it (and cascade deletes days).
5. Validation: missing name → 400; unknown exerciseId → 400/404.

- [ ] **Step 8: Client `client/src/routineApi.ts` + `client/src/Routines.tsx`**

- `routineApi.ts`: `getRoutines`, `createRoutine`, `updateRoutine`, `deleteRoutine`.
- `Routines.tsx` (default export `{ token }`): list routines; create/edit a routine with a name + expandable days, each day with a list of exercises chosen from the shared library (a mini exercise search input) and target sets/reps/weight. This screen is the "planning" side; the execution side is Task W4. Keep the UI Tailwind-consistent.

---

## Task W3: Extend the session model — WorkoutExercise, set types, rest timer metadata

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/workoutsv3.ts` OR modify existing `server/src/workouts.ts` (prefer modifying existing + new exports)
- Modify: `server/src/workouts.test.ts`
- Modify: `shared/src/index.ts`
- Modify: `client/src/workoutApi.ts`

**Interfaces:**
- Consumes: existing `Workout`/`WorkoutSet`, new `Exercise`, `Routine*` models.
- Produces: `WorkoutExercise` model (a workout contains ordered `WorkoutExercise`, each containing `WorkoutSet` rows); `WorkoutSet` gains `order`, `setType` (String: working|warmup|dropset|failure), `restSeconds`, `rpe`, `notes`, `isCompleted`. Backward-compatible: keep `WorkoutSet.exercise` free-text as a fallback OR migrate an existing `WorkoutSet.exercise` to a WorkoutExercise-driven model. REST: `POST /api/workouts/:id/exercises/:xeId/sets`, `POST /api/workouts/:id/exercises`, `PUT /api/workouts/:id/exercises/:xeId`. Shared types `WorkoutExerciseView`, `WorkoutSetInput` (with setType/restSeconds).

- [ ] **Step 1: Prisma model changes**

Modify `WorkoutSet`:
```prisma
model WorkoutSet {
  id             String  @id @default(cuid())
  ownerId        String
  workoutExerciseId String?
  workoutExercise  WorkoutExercise? @relation(fields: [workoutExerciseId], references: [id], onDelete: Cascade)
  workoutId      String
  workout        Workout  @relation(fields: [workoutId], references: [id], onDelete: Cascade)
  exercise       String             // free-text name (kept), used when workoutExerciseId is null
  order          Int      @default(0)
  setType        String   @default("working") // working|warmup|dropset|failure
  weightKg       Float
  reps           Int
  restSeconds    Int?
  rpe            Float?
  notes          String?
  createdAt      DateTime @default(now())

  @@index([ownerId, exercise])
  @@index([workoutExerciseId])
}
```
Add new model:
```prisma
model WorkoutExercise {
  id         String  @id @default(cuid())
  ownerId    String
  workoutId  String
  workout    Workout @relation(fields: [workoutId], references: [id], onDelete: Cascade)
  exerciseId String?
  exercise   Exercise? @relation(fields: [exerciseId], references: [id])
  exerciseName String        // denormalized name for display when no exerciseId
  order      Int     @default(0)
  notes      String?
  createdAt  DateTime @default(now())

  sets       WorkoutSet[]
  @@index([ownerId])
}
```
(WorkoutExercise.exerciseId nullable => supports both library exercises and free-text.)

- [ ] **Step 2: Regenerate + push.**

- [ ] **Step 3: Shared types** — extend `WorkoutSetInput` with `setType?`, `restSeconds?`, `rpe?`, and add `WorkoutExerciseView { id, order, exerciseId?, exerciseName, sets: WorkoutSetView[] }` where `WorkoutSetView = WorkoutSetInput + { id, setType }`.

- [ ] **Step 4: Extend `server/src/workouts.ts`** (non-breaking)
- Keep existing routes working (backward compat for the old single-form flow).
- `POST /api/workouts/:id/exercises` — add a `WorkoutExercise` to a workout (require `exerciseId` or `exerciseName`). Body `{ exerciseId?, exerciseName?, notes? }`. Returns the created `WorkoutExercise`.
- `POST /api/workouts/:id/exercises/:xeId/sets` — append a set to that exercise. Body `{ weightKg, reps, setType?, restSeconds?, rpe? }` (validated; setType must be in allowed set). Returns created set.
- `GET /api/workouts/:id` — now also returns `exercises: WorkoutExerciseView[]` (with sets) plus keep legacy `sets` for compat.
- Validation helper for setType: `["working","warmup","dropset","failure"]`.

- [ ] **Step 5: Tests** — add cases: create a WorkoutExercise on a workout; append a set with `setType:"warmup"`; `GET` detail includes exercises+sorted sets; invalid setType → 400. Keep existing workout tests passing.

- [ ] **Step 6: Client `client/src/workoutApi.ts`** — add `addWorkoutExercise`, `appendSetToExercise`, and extend types.

---

## Task W4: Active-session UX (workout-from-routine + fast set logging + rest timer + prefill)

**Files:**
- Create: `client/src/ActiveWorkout.tsx`
- Modify: `client/src/Workouts.tsx`
- Modify: `client/src/App.tsx` (tab still renders `Workouts`; the active session lives inside `Workouts` or as a full-screen view — recommended: internal state in `Workouts.tsx`)
- Modify: `client/src/workoutApi.ts`

**Interfaces:**
- Consumes: `Exercise`, `Routine`, `WorkoutExercise`, `progressiveSuggestion` server, `ExerciseProgression` (Task W5).
- Produces: A start-workout flow + active-session screen.

- [ ] **Step 1: Start-workout options in `Workouts.tsx`**

Replace the header with three entry points:
1. **Start Empty Workout** — opens the active session with no exercises; user adds exercises from the library mid-session.
2. **Start from Routine** — pick one of the user's `RoutineDay`s; creates a `Workout`, populates `WorkoutExercise`s from the routine's exercises (with target sets/reps) and pre-fills previous performance.
3. **Recent Workouts** — history list (existing).

- [ ] **Step 2: ActiveWorkout component**

Core loop for each exercise:
- Show exercise name + muscle group badge.
- Show **target** (e.g. `3 × 8`) from the routine/progression, and **previous** session's weight+reps in gray (prefill per set).
- Each set row: `[weight] [reps] [set-type] [✓]` — one-tap confirm. Large touch targets (px-4 py-3).
- After ✓, auto-start a **rest timer** (default 90s, configurable per exercise) with visible countdown; on done, sound/vibrate optional (simple: play a short beep via Web Audio; skip if not supported).
- Marking a set `< 48px` inputs — keep deletions/typing simple (single input per field).
- Buttons: **Add Exercise** (search library, add `WorkoutExercise`), **Finish Workout** (POST full session → summary).

- [ ] **Step 3: Prefill previous performance**

When starting from a routine, for each routine day exercise, look up the last time that exercise was performed (query `WorkoutSet` grouped by `exercise` name, most recent `Workout`) and pre-fill weight/reps. Implement a small server endpoint if needed: `GET /api/workouts/last/:exerciseName` returning `{ weightKg, reps }` of the most recent set for that exercise name.

- [ ] **Step 4: Finish flow**

On **Finish**, confirm; compute session summary (duration, exercises count, total working volume, PRs if any — Task W5), save, and return to the history view refreshing the list. Keep the session draft autosaved in component state (no persistence required this pass; acceptable for a single-user web app — note as a known limitation).

- [ ] **Step 5: Wire into `Workouts.tsx`** — the tab renders either the active-session view (when a session is active) or the start/history view.

---

## Task W5: Progressive overload (persistent targets) + PR/volume analytics

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/progression.ts` (router + helpers)
- Modify: `server/src/workouts.ts` (hook: on set log, update `ExerciseProgression` + evaluate `PersonalRecord`)
- Create: `server/src/progression.test.ts`
- Modify: `server/src/index.ts`
- Modify: `shared/src/index.ts`
- Create: `client/src/analyticsApi.ts`
- Modify: `client/src/Workouts.tsx` / create `client/src/Analytics.tsx`

**Interfaces:**
- Consumes: `WorkoutSet`, `Exercise`, `ExerciseProgression`, `PersonalRecord`.
- Produces: `ExerciseProgression` (target vs actual persistent state per exercise) and `PersonalRecord` (denormalized PR per rep-range with e1RM); REST `GET /api/workouts/analytics/exercise/:exerciseId` (or by name) returning e1RM trend + volume; `GET /api/workouts/analytics/summary` returning PRs + weekly volume by muscle group; `PUT /api/progression/:exerciseId` to set/override targets.

- [ ] **Step 1: Prisma models**

```prisma
model ExerciseProgression {
  id            String   @id @default(cuid())
  ownerId       String
  exerciseId    String?
  exerciseName  String           // keyed by name for free-text compat
  targetSets    Int      @default(3)
  targetReps    Int      @default(8)
  targetWeight  Float?
  incrementKg   Float    @default(2.5)
  lastProgressed DateTime?
  createdAt     DateTime @default(now())
  updatedAt     DateTime @updatedAt

  @@unique([ownerId, exerciseName])
  @@index([ownerId])
}

model PersonalRecord {
  id          String  @id @default(cuid())
  ownerId     String
  exerciseId  String?
  exerciseName String        // keyed by name
  repRange    Int    // 1,3,5,8,10,12
  weight      Float
  date        DateTime
  e1rm        Float

  @@unique([ownerId, exerciseName, repRange])
  @@index([ownerId])
}
```

- [ ] **Step 2: Regenerate + push.**

- [ ] **Step 3: Progression helper module** (`server/src/progression.ts`)

Compute & update on each working-set log:
- `bestE1rm = weight * (1 + reps / 30)` (Epley).
- Upsert `PersonalRecord`: for `repRange = reps` (clamped to nearest of {1,3,5,8,10,12}), if weight > current best → update.
- `ExerciseProgression`: set `targetWeight`/`targetSets`/`targetReps` if not yet set; when a full session's working sets for an exercise all meet `targetSets`×`targetReps` → `lastProgressed = now`, `targetWeight += incrementKg` (persist the proposed next target). Expose a function `suggestNext(exerciseName, workingSets)` returning the same shape as the existing `progressiveSuggestion` (keep old endpoint working).
- Export `computeAnalytics(ownerId)` returning: per-exercise e1RM trend, session volume, weekly volume by muscle group, PR list, and whether to deload (defer deload — mark as out of scope).

- [ ] **Step 4: REST**
- `GET /api/workouts/analytics/summary` → `{ totalWorkouts, thisWeekWorkouts, volumeTrend: {date,kg}[] , muscleVolume: {muscle,kg}[], prs: PersonalRecord[] }`.
- `GET /api/progression` → list of `ExerciseProgression` (target vs actual).
- `PUT /api/progression/:exerciseName` → set/update targets (`targetSets`, `targetReps`, `targetWeight`, `incrementKg`).
- Hook in `workouts.ts` `POST /:id/exercises/:xeId/sets` and legacy `POST /:id/sets` to call the progression+PR updater for `setType === "working"`.

- [ ] **Step 5: Tests** — append a set with reps hitting the target → a `PersonalRecord` is created / updated; `progression` reflects `targetWeight` bump after meeting target; analytics summary returns expected counts. Keep all existing tests green.

- [ ] **Step 6: Client**
- `client/src/analyticsApi.ts`: `getAnalytics(token)`, `getProgressions(token)`, `setProgression(token, name, input)`.
- Modify `Workouts.tsx` (or new `Analytics.tsx`): show a **progress panel** — target vs actual per exercise (the "Torqe gap": persistent goal shown beside what you actually did), e1RM trend line chart (reuse `ProgressionChart`), recent PRs list with `+2.5kg` suggestion badges, and weekly volume by muscle group (simple bar list). Keep charts plain SVG, no chart lib.

---

## Task W6: Final integration + polish + tests (Whole workout tab)

**Files:**
- Modify: `client/src/App.tsx`, `client/src/Workouts.tsx`, `client/src/ActiveWorkout.tsx`, `client/src/Routines.tsx`, `client/src/Analytics.tsx`
- Modify: `docs/...` (README notes)

**Interfaces:** Wire the whole tab together; ensure no regressions to weekly-goal auto hook, all tests green, typecheck clean, client builds.

- [ ] **Wiring** — The Workouts tab now offers: **Routines** (planning), **Start Workout** (empty or from routine; active session with fast set logging + rest timer + prefill), **History**, **Analytics** (progress/PR/volume). Confirm the weekly-goal auto-hook fires on routine-based workout creation.
- [ ] **Empty/edge states** — no routines yet → prompt to create or use Empty; no exercises → show library import; no data → friendly empty states. First logged workout in <2 min.
- [ ] **PWA/typecheck/build verification**:
  - `npm run typecheck`
  - `npm run build --workspace client`
  - `npm test --workspace server`
- [ ] **README** — note the new workout module + how exercises/routines/progression work.

---

## Deferred (explicitly out of scope this pass — from research)

- Supersets, warm-up calculator, plate calculator, RPE/RIR advanced fields (keep `rpe` field in schema but no UI this pass), CSV export, muscle heatmap, deload detection, AI workout generation, social features, body-metrics, video demos, gamification.

---

## Workout Tab Self-Review

- **Spec/domain coverage:** Exercise library (§: workouts must allow known, categorized exercises — was free-text), routines/templates, workout-from-routine, fast set logging with previous-performance prefill + rest timer, set types (working/warm-up), progressive overload with **persistent target-vs-actual** (the "Torqe gap" differentiator), PR detection + e1RM (Epley) + volume analytics. This closes the gap between our bare CRUD and commercial apps (Strong/Hevy/Jefit/Boostcamp/5x5).
- **Cross-module:** weekly-goal auto-hook (workout POST → GoalEvent) preserved; global shared exercise library mirrors the now-global Food catalog; custom exercises user-owned.
- **Single-user + SQLite constraints honored:** all user rows `ownerId`-scoped; no Prisma enums (String fields validated in routes); additive `db push`.

## Open Questions / Notes for the implementer

1. Whether to keep the legacy free-text `WorkoutSet.exercise` + old set-logging form for backward compatibility (recommended: YES, keep it working; new UX is additive).
2. Rest timer sound: implement a tiny Web Audio beep; no external library.
3. `ExerciseProgression` keyed by `exerciseName` (free-text compat) rather than `exerciseId` — keeps legacy sets working.
