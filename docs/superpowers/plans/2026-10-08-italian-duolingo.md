# Italian Duolingo-Style Tab (Web + Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Italian tab into a Duolingo-style learning app (path of units/lessons, 5 exercise types, XP, hearts, streak, mistake review) on both the web client and the Android app, with progress feeding the existing Goals tab.

**Architecture:** A hand-authored, static Italian course lives in `server/src/content/italianCourse.ts` (units → lessons → exercises with answer keys). A new `/api/italian-path` router stores per-user attempts/completions in two new Prisma models, grades submissions authoritatively (deterministic, no LLM), awards XP, computes the streak, and emits `GoalEvent`s through a new shared `emitGoalSignals` helper (`italian_lesson` + new `italian_xp` auto-sources). The web client grades locally for instant feedback and submits each session once; the Android app consumes the same endpoints (adding an `Authorization` header, which its Ktor client currently lacks) and mirrors the grading in Kotlin. Hearts are client-side session state (5 per session).

**Tech Stack:** Express 4 + Prisma 5/PostgreSQL + vitest/supertest (server), React 18 + TS + Tailwind (client), Kotlin Multiplatform Compose + Hilt + Ktor (Android).

**Spec:** User decisions — extend the existing web Italian tab (keep the LLM "Daily Lesson" as a sub-tab); hand-authored JSON course seed; MVP mechanics = path + 5 exercise types + XP + streak + hearts + mistake review (no leagues/gems); Goals link = reuse `autoSource` + add `italian_xp` + surface an Italian card in the Goals tab; Android app gets the same feature as a new bottom-nav tab.

## Global Constraints

- Windows/cmd shell: use `dir` / `findstr`, not `ls` / `grep`. Paths with spaces need quotes.
- Server patterns: every row scoped by `ownerId` via `findFirst({ where: { id, ownerId: req.userId } })`; hand-validate → `400 {error}`; 404 → `{ error: "not found" }`; create → `201`; delete → `204`; `router.use(requireAuth)`. Per `.opencode/skills/express-prisma-endpoint`.
- Client patterns: one `*Api.ts` helper per resource using `authedFetch(token, path, init)`; components = props + local state; never inline `fetch`. Per `.opencode/skills/react-ts-component`.
- `shared/src/index.ts` and `server/src/shared.d.ts` are manual mirrors (server tsconfig path-maps `shared` → the `.d.ts`); **every shared type must be appended identically to both files**.
- No comments in generated code.
- Run server tests: `npm test --workspace server` (requires the `dtd_test` Postgres DB; `fileParallelism: false`).
- Typecheck: `npm run typecheck` (root).
- Android compile gate: `gradlew.bat :composeApp:compileDebugKotlinAndroid` — **only run this yourself, at the end, never from a subagent** (lock contention, per `HANDOFF-2026-10-06.md`). Redirect output to a file and check with `findstr /b "e:" file`.
- Android subagents must receive **non-overlapping explicit file lists** (HANDOFF warning: a previous subagent truncated `ExerciseAnimationView.kt`).
- XP rules (authoritative, server-side): first correct attempt per exercise ever = +1 XP; first completion of a lesson = +10 XP; each replay completion = +5 XP. `xpTotal = SUM(ItalianExerciseAttempt.xpAwarded) + SUM(ItalianLessonCompletion.xpAwarded)`.
- Hearts: 5 per lesson session, client-side only; session ends at 0.
- Lesson unlock rule: first lesson of the course is always unlocked; any other lesson is unlocked iff the previous lesson (in course order) is completed.
- New auto-sources: `italian_lesson` (existing) + `italian_xp` (new, `count` = XP gained).

## File Structure

**Server (create):**
- `server/src/content/italianCourse.ts` — static `ITALIAN_COURSE` constant (all content).
- `server/src/italianPathGrade.ts` — pure grading: `normalizeAnswer`, `gradeExercise`.
- `server/src/italianPathGrade.test.ts` — unit tests for grading.
- `server/src/goalsAuto.ts` — `emitGoalSignals(ownerId, signals)`.
- `server/src/italianPath.ts` — `italianPathRouter` (`/course`, `/progress`, `/lesson/:lessonId/submit`).
- `server/src/italianPath.test.ts` — supertest integration tests.

**Server (modify):**
- `server/prisma/schema.prisma` — add `ItalianLessonCompletion`, `ItalianExerciseAttempt`.
- `server/src/italian.ts` — delegate goal emission to `emitGoalSignals`.
- `server/src/index.ts` — mount `app.use("/api/italian-path", italianPathRouter)` (after line 48).

**Shared (modify, mirrored):**
- `shared/src/index.ts` + `server/src/shared.d.ts` — course/answer/progress/result types.

**Client (create):**
- `client/src/italianPathLogic.ts` — client copy of grading + `isLessonUnlocked`, `nextLessonId`, `lessonStatuses`.
- `client/src/italianPathLogic.test.ts` — unit tests.
- `client/src/ItalianPath.tsx` — path screen (header stats + units + lesson nodes).
- `client/src/ItalianLessonRunner.tsx` — exercise session player.
- `client/src/ItalianGoalCard.tsx` — Italian progress card for the Goals tab.

**Client (modify):**
- `client/src/italianApi.ts` — `getCourse`, `getPathProgress`, `submitPathLesson`.
- `client/src/ItalianView.tsx` — sub-tabs: Path (default) / Daily Lesson.
- `client/src/Goals.tsx` — add `italian_xp` to `AUTOSOURCE_OPTIONS`, render `<ItalianGoalCard>`.

**Android (create):**
- `composeApp/src/commonMain/kotlin/com/daytoday/network/ItalianDtos.kt` — DTOs + `ItalianGrading` pure Kotlin (`normalizeAnswer`, `gradeExercise`).
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ItalianViewModel.kt` — state machine (course/progress/session).
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ItalianScreen.kt` — path UI + session UI.
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ExerciseViews.kt` — per-exercise composables + TTS.
- `composeApp/src/androidMain/kotlin/com/daytoday/settings/ItalianGoals.kt` — `recordItalianLessonCompleted(settingsManager)`.

**Android (modify):**
- `composeApp/src/commonMain/kotlin/com/daytoday/network/DayTodayApi.kt` — 3 methods taking `authToken: String`.
- `composeApp/src/androidMain/kotlin/com/daytoday/network/DayTodayApiImpl.kt` — implement them with `header("Authorization", "Bearer $authToken")`.
- `composeApp/src/androidMain/kotlin/com/daytoday/settings/GoalEntry.kt` — `GoalType.ITALIAN_LESSONS` + shared `weekStart()` / `weeklyReset()` helpers.
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/goals/GoalsViewModel.kt` — use shared helpers; current value for the new type; keep existing behavior identical.
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt`, `NavHost.kt`, `ui/screen/BottomNavHost.kt` — new `Screen.Italian` route + tab.

---

### Task 1: Shared types (both mirrors)

**Files:**
- Modify: `shared/src/index.ts` (append after `ItalianProgress`, line 167)
- Modify: `server/src/shared.d.ts` (append the identical block after `ItalianProgress`)

**Interfaces:**
- Produces: all types used by Tasks 4–9 and 11 (`ItalianCourse`, `ItalianExercise`, `ItalianAnswer`, `ItalianPathProgress`, `ItalianSubmitResult`).

- [ ] **Step 1: Append this block to the end of `shared/src/index.ts` (before the `SettingsResponse` block is fine; append at EOF to keep it simple)**

```ts
export interface ItalianChoiceExercise {
  id: string;
  kind: "choice";
  direction: "en_to_it" | "it_to_en" | "listen";
  prompt: string;
  choices: string[];
  answerIndex: number;
  speak?: string;
}

export interface ItalianTypeExercise {
  id: string;
  kind: "type";
  prompt: string;
  accepted: string[];
}

export interface ItalianMatchExercise {
  id: string;
  kind: "match";
  pairs: { left: string; right: string }[];
}

export type ItalianExercise = ItalianChoiceExercise | ItalianTypeExercise | ItalianMatchExercise;

export interface ItalianLessonDef {
  id: string;
  title: string;
  exercises: ItalianExercise[];
}

export interface ItalianUnitDef {
  id: string;
  title: string;
  lessons: ItalianLessonDef[];
}

export interface ItalianCourse {
  language: "it";
  units: ItalianUnitDef[];
}

export type ItalianAnswer =
  | { exerciseId: string; kind: "choice"; choiceIndex: number }
  | { exerciseId: string; kind: "type"; text: string }
  | { exerciseId: string; kind: "match"; pairs: { left: string; right: string }[] };

export interface ItalianReviewDue {
  lessonId: string;
  exerciseId: string;
}

export interface ItalianPathProgress {
  xp: number;
  streak: number;
  completedLessonIds: string[];
  totalLessons: number;
  reviewDue: ItalianReviewDue[];
}

export interface ItalianSubmitResult {
  results: { exerciseId: string; isCorrect: boolean }[];
  xpGained: number;
  lessonCompleted: boolean;
  progress: ItalianPathProgress;
}
```

- [ ] **Step 2: Append the exact same block to `server/src/shared.d.ts`.** Verify both match:

Run: `findstr /c:"ItalianSubmitResult" shared\src\index.ts server\src\shared.d.ts`
Expected: both files listed.

- [ ] **Step 3: Typecheck**

Run: `npm run typecheck`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/index.ts server/src/shared.d.ts
git commit -m "feat(italian): shared types for duolingo-style path"
```

---

### Task 2: Prisma models + migration

**Files:**
- Modify: `server/prisma/schema.prisma` (append after `PracticeLog`, line 350)

**Interfaces:**
- Produces: Prisma models `ItalianLessonCompletion`, `ItalianExerciseAttempt` used by Task 6.

- [ ] **Step 1: Append to `server/prisma/schema.prisma`**

```prisma
model ItalianLessonCompletion {
  id          String   @id @default(cuid())
  ownerId     String
  lessonId    String
  xpAwarded   Int      @default(0)
  completedAt DateTime @default(now())
  updatedAt   DateTime @updatedAt

  @@unique([ownerId, lessonId])
  @@index([ownerId])
}

model ItalianExerciseAttempt {
  id         String   @id @default(cuid())
  ownerId    String
  lessonId   String
  exerciseId String
  isCorrect  Boolean
  userAnswer String
  xpAwarded  Int      @default(0)
  createdAt  DateTime @default(now())

  @@index([ownerId, createdAt])
  @@index([ownerId, exerciseId])
}
```

- [ ] **Step 2: Generate client + create migration**

Run: `npx prisma generate --schema server/prisma/schema.prisma`
Expected: `Generated Prisma Client` message.

Run: `npm run db:migrate --workspace server -- --name add_italian_path`
Expected: migration applied, `The following migration was created`.

- [ ] **Step 3: Commit**

```bash
git add server/prisma/schema.prisma server/prisma/migrations
git commit -m "feat(italian): prisma models for path attempts and completions"
```

---

### Task 3: `emitGoalSignals` refactor (keep existing behavior)

**Files:**
- Create: `server/src/goalsAuto.ts`
- Modify: `server/src/italian.ts` (replace lines 120–131)
- Test: existing `server/src/goals.test.ts`, `server/src/italian.test.ts` (unchanged)

**Interfaces:**
- Produces: `export async function emitGoalSignals(ownerId: string, signals: Record<string, number>): Promise<void>` — used by Task 6.
- Consumes: `prisma.weeklyGoal`, `prisma.goalEvent` (existing).

- [ ] **Step 1: Create `server/src/goalsAuto.ts`**

```ts
import { prisma } from "./db";

export async function emitGoalSignals(ownerId: string, signals: Record<string, number>): Promise<void> {
  const sources = Object.entries(signals).filter(([, count]) => count > 0);
  if (sources.length === 0) return;
  const goals = await prisma.weeklyGoal.findMany({
    where: { ownerId, autoSource: { in: sources.map(([s]) => s) } }
  });
  for (const goal of goals) {
    const count = signals[goal.autoSource!];
    if (!count || count <= 0) continue;
    await prisma.goalEvent.create({
      data: { ownerId, goalId: goal.id, date: new Date(), count, source: goal.autoSource! }
    });
  }
}
```

- [ ] **Step 2: Replace in `server/src/italian.ts`**

Delete the `LESSON_AUTO_SOURCES` const and the local `incrementWeeklyGoals` function (lines 120–131). Replace the call inside `maybeCompleteLesson` (line 146) with:

```ts
await emitGoalSignals(ownerId, { italian_lesson: 1, italian_words: 1 });
```

Add the import at the top:

```ts
import { emitGoalSignals } from "./goalsAuto";
```

Behavior note: previously every goal with `autoSource` in (`italian_lesson`,`italian_words`) got one `GoalEvent(count 1)`; the new call produces exactly the same events.

- [ ] **Step 3: Run existing tests**

Run: `npm test --workspace server`
Expected: all PASS (no goal/italian regressions).

- [ ] **Step 4: Commit**

```bash
git add server/src/goalsAuto.ts server/src/italian.ts
git commit -m "refactor(goals): extract emitGoalSignals helper"
```

---

### Task 4: Course content module

**Files:**
- Create: `server/src/content/italianCourse.ts`

**Interfaces:**
- Produces: `export const ITALIAN_COURSE: ItalianCourse` — consumed by Tasks 6 (router) and exported through `/api/italian-path/course`.

- [ ] **Step 1: Create `server/src/content/italianCourse.ts`**

Unit 1 is fully authored below (3 lessons × 6 exercises). Units 2 and 3 follow the exact briefs given; the implementing engineer writes the concrete exercise objects in the same shape (6 exercises per lesson, mixing `choice` (`en_to_it`, `it_to_en`, `listen`), `type`, and `match`; ids `u<N>l<M>e<K>`; every `choice` needs a valid `answerIndex`; every `type` needs ≥1 `accepted` string; every `match` needs 4 pairs).

```ts
import type { ItalianCourse } from "shared";

export const ITALIAN_COURSE: ItalianCourse = {
  language: "it",
  units: [
    {
      id: "u1",
      title: "Basics",
      lessons: [
        {
          id: "u1l1",
          title: "Greetings",
          exercises: [
            { id: "u1l1e1", kind: "choice", direction: "en_to_it", prompt: "hello", choices: ["ciao", "grazie", "arrivederci", "scusi"], answerIndex: 0 },
            { id: "u1l1e2", kind: "choice", direction: "it_to_en", prompt: "Buongiorno!", choices: ["Good night", "Good morning", "Goodbye", "Thank you"], answerIndex: 1 },
            { id: "u1l1e3", kind: "type", prompt: "Write in Italian: Thank you", accepted: ["grazie"] },
            { id: "u1l1e4", kind: "match", pairs: [{ left: "ciao", right: "hello" }, { left: "grazie", right: "thank you" }, { left: "prego", right: "you're welcome" }, { left: "scusi", right: "excuse me" }] },
            { id: "u1l1e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Goodbye", "Hello", "Please", "Sorry"], answerIndex: 0, speak: "Arrivederci" },
            { id: "u1l1e6", kind: "choice", direction: "en_to_it", prompt: "good evening", choices: ["buonasera", "buongiorno", "buonanotte", "ciao"], answerIndex: 0 }
          ]
        },
        {
          id: "u1l2",
          title: "Introductions",
          exercises: [
            { id: "u1l2e1", kind: "choice", direction: "en_to_it", prompt: "My name is…", choices: ["Mi chiamo…", "Come stai?", "Dov'è?", "Quanto costa?"], answerIndex: 0 },
            { id: "u1l2e2", kind: "choice", direction: "it_to_en", prompt: "Come ti chiami?", choices: ["How are you?", "What is your name?", "Where are you?", "How old are you?"], answerIndex: 1 },
            { id: "u1l2e3", kind: "type", prompt: "Write in Italian: I am American", accepted: ["sono americano", "sono americana"] },
            { id: "u1l2e4", kind: "match", pairs: [{ left: "mi chiamo", right: "my name is" }, { left: "piacere", right: "nice to meet you" }, { left: "come stai", right: "how are you" }, { left: "molto bene", right: "very good" }] },
            { id: "u1l2e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Nice to meet you", "See you later", "How are you?", "Where are you from?"], answerIndex: 0, speak: "Piacere di conoscerti" },
            { id: "u1l2e6", kind: "choice", direction: "en_to_it", prompt: "Where are you from?", choices: ["Di dove sei?", "Che ore sono?", "Come ti chiami?", "Cosa mangi?"], answerIndex: 0 }
          ]
        },
        {
          id: "u1l3",
          title: "Polite phrases",
          exercises: [
            { id: "u1l3e1", kind: "choice", direction: "en_to_it", prompt: "please", choices: ["per favore", "grazie", "scusi", "arrivederci"], answerIndex: 0 },
            { id: "u1l3e2", kind: "choice", direction: "it_to_en", prompt: "Mi scusi", choices: ["Excuse me", "I'm sorry", "Thank you", "Please"], answerIndex: 0 },
            { id: "u1l3e3", kind: "type", prompt: "Write in Italian: Excuse me", accepted: ["mi scusi", "scusi"] },
            { id: "u1l3e4", kind: "match", pairs: [{ left: "per favore", right: "please" }, { left: "grazie mille", right: "thanks a lot" }, { left: "mi scusi", right: "excuse me" }, { left: "nessun problema", right: "no problem" }] },
            { id: "u1l3e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Thanks a lot", "You're welcome", "Good morning", "Goodbye"], answerIndex: 0, speak: "Grazie mille" },
            { id: "u1l3e6", kind: "choice", direction: "en_to_it", prompt: "You're welcome", choices: ["prego", "grazie", "ciao", "scusi"], answerIndex: 0 }
          ]
        }
      ]
    },
    {
      id: "u2",
      title: "Numbers & Time",
      lessons: [
        { id: "u2l1", title: "Numbers 1-10", exercises: [] },
        { id: "u2l2", title: "How much does it cost?", exercises: [] },
        { id: "u2l3", title: "What time is it?", exercises: [] }
      ]
    },
    {
      id: "u3",
      title: "Food & Drink",
      lessons: [
        { id: "u3l1", title: "At the café", exercises: [] },
        { id: "u3l2", title: "Ordering food", exercises: [] },
        { id: "u3l3", title: "Tastes & ingredients", exercises: [] }
      ]
    }
  ]
};
```

Then **fill the six empty lessons** with the content briefs below (each → 6 exercises, ids `u2l1e1`…`u3l3e6`, same shapes as Unit 1; include at least one `type` and one `match` and one `listen` per lesson):

- `u2l1` Numbers 1–10 — vocab: uno, due, tre, quattro, cinque, sei, sette, otto, nove, dieci. Exercises: en_to_it MC ("five"→cinque), it_to_en MC ("sette"→"seven"), type ("Write in Italian: three" accepted ["tre"]), match (4 of uno/due/tre/quattro vs one/two/three/four), listen (speak "Cinque"), en_to_it MC ("ten"→dieci).
- `u2l2` Prices — vocab: quanto costa, euro, caro, economico. Exercises: en_to_it MC ("How much does it cost?"→"Quanto costa?"), type ("Write in Italian: it costs 5 euros" accepted ["costa 5 euro","costa cinque euro","costa 5€","costa cinque euro"]), match (quanto costa/how much does it cost, euro/euro, caro/expensive, economico/cheap), listen (speak "Quanto costa?"), it_to_en MC ("È caro"→"It's expensive"), en_to_it MC ("cheap"→economico).
- `u2l3` Time — vocab: che ore sono, è le/ora, mezzogiorno, mezzanotte, oggi, domani. Exercises: en_to_it MC ("What time is it?"→"Che ore sono?"), type ("Write in Italian: it is two o'clock" accepted ["sono le due","è le due","sono le 2","è le 2"]), match (oggi/today, domani/tomorrow, mezzogiorno/noon, mezzanotte/midnight), listen (speak "Sono le tre"), it_to_en MC ("Che ore sono?"→"What time is it?"), en_to_it MC ("tomorrow"→domani).
- `u3l1` Café — vocab: un caffè, un cappuccino, un cornetto, un bicchiere d'acqua, vorrei, il conto. Exercises: en_to_it MC ("I would like a coffee"→"Vorrei un caffè"), type ("Write in Italian: the bill please" accepted ["il conto per favore","il conto, per favore","la conto per favore"]), match (un caffè/a coffee, un cappuccino/a cappuccino, un cornetto/a croissant, il conto/the bill), listen (speak "Un cappuccino, per favore"), it_to_en MC ("Vorrei un caffè"→"I would like a coffee"), en_to_it MC ("a glass of water"→"un bicchiere d'acqua").
- `u3l2` Food — vocab: la pizza, la pasta, il pane, la zuppa, delizioso, salato. Exercises: en_to_it MC ("pizza"→la pizza), type ("Write in Italian: the bread" accepted ["il pane"]), match (la pasta/the pasta, la zuppa/the soup, delizioso/delicious, salato/salty), listen (speak "La pasta"), it_to_en MC ("La zuppa"→"The soup"), en_to_it MC ("delicious"→delizioso).
- `u3l3` Ingredients — vocab: il pomodoro, il formaggio, l'aglio, il burro, il latte, l'uovo. Exercises: en_to_it MC ("the cheese"→il formaggio), type ("Write in Italian: the milk" accepted ["il latte"]), match (il pomodoro/the tomato, l'aglio/the garlic, il burro/the butter, l'uovo/the egg), listen (speak "Il formaggio"), it_to_en MC ("L'aglio"→"The garlic"), en_to_it MC ("the egg"→l'uovo).

- [ ] **Step 2: Sanity-check the content module compiles and every lesson has 6 exercises with valid answer indexes**

Run: `npm run typecheck`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/content/italianCourse.ts
git commit -m "feat(italian): hand-authored course content (3 units)"
```

---

### Task 5: Grading module (pure, tested)

**Files:**
- Create: `server/src/italianPathGrade.ts`
- Test: `server/src/italianPathGrade.test.ts`

**Interfaces:**
- Produces: `normalizeAnswer(s: string): string`, `gradeExercise(ex: ItalianExercise, answer: ItalianAnswer): boolean` — consumed by Task 6.

- [ ] **Step 1: Write the failing tests — `server/src/italianPathGrade.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { normalizeAnswer, gradeExercise } from "./italianPathGrade";
import type { ItalianAnswer, ItalianExercise } from "shared";

const choice: ItalianExercise = {
  id: "e1", kind: "choice", direction: "en_to_it", prompt: "hello",
  choices: ["ciao", "grazie", "arrivederci", "scusi"], answerIndex: 0
};
const typeEx: ItalianExercise = { id: "e2", kind: "type", prompt: "t", accepted: ["Buongiorno", "buongiorno"] };
const matchEx: ItalianExercise = {
  id: "e3", kind: "match",
  pairs: [{ left: "ciao", right: "hello" }, { left: "grazie", right: "thank you" }]
};

describe("normalizeAnswer", () => {
  it("lowercases, strips accents and punctuation", () => {
    expect(normalizeAnswer("  BuonGiorno! ")).toBe("buongiorno");
    expect(normalizeAnswer("caffè.")).toBe("caffe");
    expect(normalizeAnswer("mi   chiamo")).toBe("mi chiamo");
  });
});

describe("gradeExercise", () => {
  it("grades choice answers", () => {
    const right: ItalianAnswer = { exerciseId: "e1", kind: "choice", choiceIndex: 0 };
    const wrong: ItalianAnswer = { exerciseId: "e1", kind: "choice", choiceIndex: 2 };
    expect(gradeExercise(choice, right)).toBe(true);
    expect(gradeExercise(choice, wrong)).toBe(false);
  });

  it("grades type answers against any accepted variant", () => {
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "type", text: "buongiorno!!" })).toBe(true);
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "type", text: "buonasera" })).toBe(false);
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "choice", choiceIndex: 0 })).toBe(false);
  });

  it("grades match answers as set equality regardless of order", () => {
    const a: ItalianAnswer = {
      exerciseId: "e3", kind: "match",
      pairs: [{ left: "grazie", right: "thank you" }, { left: "ciao", right: "hello" }]
    };
    expect(gradeExercise(matchEx, a)).toBe(true);
    const b: ItalianAnswer = {
      exerciseId: "e3", kind: "match",
      pairs: [{ left: "ciao", right: "hi" }, { left: "grazie", right: "thank you" }]
    };
    expect(gradeExercise(matchEx, b)).toBe(false);
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `npm test --workspace server -- src/italianPathGrade.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement `server/src/italianPathGrade.ts`**

```ts
import type { ItalianAnswer, ItalianExercise } from "shared";

export function normalizeAnswer(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[.,!?;:"'’]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function gradeExercise(ex: ItalianExercise, answer: ItalianAnswer): boolean {
  switch (ex.kind) {
    case "choice":
      return answer.kind === "choice" && answer.choiceIndex === ex.answerIndex;
    case "type":
      return (
        answer.kind === "type" &&
        ex.accepted.some((a) => normalizeAnswer(a) === normalizeAnswer(answer.text))
      );
    case "match": {
      if (answer.kind !== "match") return false;
      const key = (p: { left: string; right: string }) =>
        `${normalizeAnswer(p.left)}|${normalizeAnswer(p.right)}`;
      if (answer.pairs.length !== ex.pairs.length) return false;
      const have = new Set(answer.pairs.map(key));
      return ex.pairs.every((p) => have.has(key(p)));
    }
  }
}
```

- [ ] **Step 4: Run to verify passing**

Run: `npm test --workspace server -- src/italianPathGrade.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/italianPathGrade.ts server/src/italianPathGrade.test.ts
git commit -m "feat(italian): deterministic exercise grading"
```

---

### Task 6: `/api/italian-path` router + integration tests

**Files:**
- Create: `server/src/italianPath.ts`
- Modify: `server/src/index.ts` (add after line 48 `app.use("/api/italian", italianRouter);`)
- Test: `server/src/italianPath.test.ts`

**Interfaces:**
- Consumes: `ITALIAN_COURSE` (Task 4), `gradeExercise` (Task 5), `emitGoalSignals` (Task 3), `currentStreak` from `./streak`, shared types (Task 1).
- Produces (HTTP):
  - `GET /api/italian-path/course` → `ItalianCourse`
  - `GET /api/italian-path/progress` → `ItalianPathProgress`
  - `POST /api/italian-path/lesson/:lessonId/submit` body `{ answers: ItalianAnswer[] }` → `ItalianSubmitResult` (201)
- Consumed by Tasks 7 (client) and 11 (Android).

- [ ] **Step 1: Write the failing tests — `server/src/italianPath.test.ts`**

```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { ITALIAN_COURSE } from "./content/italianCourse";
import type { ItalianAnswer } from "shared";

let token = "";

function allCorrectAnswers(lessonId: string): ItalianAnswer[] {
  const lesson = ITALIAN_COURSE.units
    .flatMap((u) => u.lessons)
    .find((l) => l.id === lessonId)!;
  return lesson.exercises.map((ex) => {
    if (ex.kind === "choice") return { exerciseId: ex.id, kind: "choice", choiceIndex: ex.answerIndex };
    if (ex.kind === "type") return { exerciseId: ex.id, kind: "type", text: ex.accepted[0] };
    return { exerciseId: ex.id, kind: "match", pairs: ex.pairs };
  });
}

beforeEach(async () => {
  await prisma.italianExerciseAttempt.deleteMany({});
  await prisma.italianLessonCompletion.deleteMany({});
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.lesson.deleteMany({});
  await prisma.practiceLog.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "ip@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("course returns 3 units with lessons", async () => {
  const res = await request(app)
    .get("/api/italian-path/course")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.units).toHaveLength(3);
  const lessons = res.body.units.flatMap((u: { lessons: unknown[] }) => u.lessons);
  expect(lessons).toHaveLength(9);
  for (const l of lessons as { exercises: unknown[] }[]) {
    expect(l.exercises).toHaveLength(6);
  }
});

test("unauthenticated is rejected", async () => {
  expect((await request(app).get("/api/italian-path/course")).status).toBe(401);
  expect((await request(app).get("/api/italian-path/progress")).status).toBe(401);
});

test("submitting all correct answers completes lesson, awards xp, emits goal events", async () => {
  await request(app)
    .post("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Italian lessons", targetCount: 3, autoSource: "italian_lesson" });
  await request(app)
    .post("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Italian XP", targetCount: 100, autoSource: "italian_xp" });

  const res = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(res.status).toBe(201);
  expect(res.body.lessonCompleted).toBe(true);
  expect(res.body.xpGained).toBe(6 + 10);
  expect(res.body.progress.completedLessonIds).toContain("u1l1");
  expect(res.body.progress.xp).toBe(16);

  const goals = await request(app)
    .get("/api/goals")
    .set("Authorization", `Bearer ${token}`);
  const lessonGoal = goals.body.find((g: { autoSource: string }) => g.autoSource === "italian_lesson");
  const xpGoal = goals.body.find((g: { autoSource: string }) => g.autoSource === "italian_xp");
  expect(lessonGoal.thisWeekCount).toBe(1);
  expect(xpGoal.thisWeekCount).toBe(16);
});

test("replaying a completed lesson awards replay xp only", async () => {
  const first = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(first.body.xpGained).toBe(16);

  const second = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(second.body.lessonCompleted).toBe(false);
  expect(second.body.xpGained).toBe(5);
  expect(second.body.progress.xp).toBe(21);
});

test("wrong answers do not complete the lesson and count for review", async () => {
  const answers: ItalianAnswer[] = [
    { exerciseId: "u1l1e1", kind: "choice", choiceIndex: 1 },
    { exerciseId: "u1l1e2", kind: "choice", choiceIndex: 1 },
    { exerciseId: "u1l1e3", kind: "type", text: "grazie" },
    { exerciseId: "u1l1e4", kind: "match", pairs: ITALIAN_COURSE.units[0].lessons[0].exercises[3].kind === "match" ? ITALIAN_COURSE.units[0].lessons[0].exercises[3].pairs : [] },
    { exerciseId: "u1l1e5", kind: "choice", choiceIndex: 2 },
    { exerciseId: "u1l1e6", kind: "choice", choiceIndex: 0 }
  ];
  const res = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers });
  expect(res.body.lessonCompleted).toBe(false);
  const review = res.body.progress.reviewDue as { exerciseId: string }[];
  expect(review.map((r) => r.exerciseId)).toEqual(
    expect.arrayContaining(["u1l1e1", "u1l1e5"])
  );
  expect(review.map((r) => r.exerciseId)).not.toContain("u1l1e3");
});

test("rejects unknown lesson and unknown exercise", async () => {
  const a = await request(app)
    .post("/api/italian-path/lesson/nope/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: [] });
  expect(a.status).toBe(404);

  const b = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: [{ exerciseId: "zzz", kind: "choice", choiceIndex: 0 }] });
  expect(b.status).toBe(400);
});

test("streak is 1 after activity today", async () => {
  await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  const res = await request(app)
    .get("/api/italian-path/progress")
    .set("Authorization", `Bearer ${token}`);
  expect(res.body.streak).toBe(1);
  expect(res.body.totalLessons).toBe(9);
});
```

- [ ] **Step 2: Run to verify failure**

Run: `npm test --workspace server -- src/italianPath.test.ts`
Expected: FAIL (router not mounted / module not found).

- [ ] **Step 3: Implement `server/src/italianPath.ts`**

```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { ITALIAN_COURSE } from "./content/italianCourse";
import { gradeExercise } from "./italianPathGrade";
import { emitGoalSignals } from "./goalsAuto";
import { currentStreak } from "./streak";
import type {
  ItalianAnswer,
  ItalianExercise,
  ItalianLessonDef,
  ItalianPathProgress,
  ItalianSubmitResult
} from "shared";

const EXERCISE_XP = 1;
const FIRST_COMPLETION_XP = 10;
const REPLAY_XP = 5;

const ALL_LESSONS: ItalianLessonDef[] = ITALIAN_COURSE.units.flatMap((u) => u.lessons);
const EXERCISE_INDEX: Map<string, { lessonId: string; exercise: ItalianExercise }> = new Map(
  ALL_LESSONS.flatMap((l) => l.exercises.map((e) => [e.id, { lessonId: l.id, exercise: e }]))
);

export function findLesson(lessonId: string): ItalianLessonDef | undefined {
  return ALL_LESSONS.find((l) => l.id === lessonId);
}

async function buildProgress(ownerId: string): Promise<ItalianPathProgress> {
  const [completions, attempts, wrongAttempts] = await Promise.all([
    prisma.italianLessonCompletion.findMany({ where: { ownerId }, select: { lessonId: true, xpAwarded: true } }),
    prisma.italianExerciseAttempt.findMany({
      where: { ownerId, isCorrect: true },
      select: { exerciseId: true, createdAt: true }
    }),
    prisma.italianExerciseAttempt.findMany({
      where: { ownerId, isCorrect: false },
      select: { exerciseId: true, createdAt: true }
    })
  ]);
  const correctIds = new Set(attempts.map((a) => a.exerciseId));
  const reviewDue = wrongAttempts
    .filter((a) => !correctIds.has(a.exerciseId))
    .map((a) => ({ lessonId: EXERCISE_INDEX.get(a.exerciseId)?.lessonId ?? "", exerciseId: a.exerciseId }))
    .filter((r, i, arr) => arr.findIndex((x) => x.exerciseId === r.exerciseId) === i);

  const xp =
    (await prisma.italianExerciseAttempt.aggregate({ where: { ownerId }, _sum: { xpAwarded: true } }))._sum
      .xpAwarded ?? 0 +
    (await prisma.italianLessonCompletion.aggregate({ where: { ownerId }, _sum: { xpAwarded: true } }))._sum
      .xpAwarded ?? 0;

  const activeDates = [...attempts.map((a) => a.createdAt)];
  for (const c of completions) {
    const lesson = findLesson(c.lessonId);
    if (lesson) activeDates.push(new Date());
  }
  const todayActive = activeDates.some((d) => d.toDateString() === new Date().toDateString());
  const anchor = todayActive ? new Date() : new Date(Date.now() - 86_400_000);
  const streak = activeDates.length === 0 ? 0 : currentStreak(activeDates, anchor);

  return {
    xp,
    streak,
    completedLessonIds: completions.map((c) => c.lessonId),
    totalLessons: ALL_LESSONS.length,
    reviewDue
  };
}

export const italianPathRouter = Router();
italianPathRouter.use(requireAuth);

italianPathRouter.get("/course", (_req, res) => {
  res.json(ITALIAN_COURSE);
});

italianPathRouter.get("/progress", async (req: AuthedRequest, res) => {
  res.json(await buildProgress(req.userId!));
});

italianPathRouter.post("/lesson/:lessonId/submit", async (req: AuthedRequest, res) => {
  const lesson = findLesson(req.params.lessonId);
  if (!lesson) return res.status(404).json({ error: "not found" });

  const rawAnswers = req.body?.answers;
  if (!Array.isArray(rawAnswers)) return res.status(400).json({ error: "answers required" });

  const answers: ItalianAnswer[] = [];
  const lessonExerciseIds = new Set(lesson.exercises.map((e) => e.id));
  for (const a of rawAnswers) {
    if (!a || typeof a.exerciseId !== "string" || !lessonExerciseIds.has(a.exerciseId)) {
      return res.status(400).json({ error: "unknown exercise" });
    }
    if (a.kind !== "choice" && a.kind !== "type" && a.kind !== "match") {
      return res.status(400).json({ error: "invalid answer" });
    }
    answers.push(a as ItalianAnswer);
  }

  const ownerId = req.userId!;
  const previouslyCorrect = new Set(
    (
      await prisma.italianExerciseAttempt.findMany({
        where: { ownerId, exerciseId: { in: [...lessonExerciseIds] }, isCorrect: true },
        select: { exerciseId: true }
      })
    ).map((r) => r.exerciseId)
  );

  const results: { exerciseId: string; isCorrect: boolean }[] = [];
  let attemptXp = 0;
  for (const answer of answers) {
    const ex = EXERCISE_INDEX.get(answer.exerciseId)!.exercise;
    const isCorrect = gradeExercise(ex, answer);
    const xp = isCorrect && !previouslyCorrect.has(answer.exerciseId) ? EXERCISE_XP : 0;
    attemptXp += xp;
    previouslyCorrect.add(answer.exerciseId);
    results.push({ exerciseId: answer.exerciseId, isCorrect });
    await prisma.italianExerciseAttempt.create({
      data: {
        ownerId,
        lessonId: lesson.id,
        exerciseId: answer.exerciseId,
        isCorrect,
        userAnswer: JSON.stringify(answer),
        xpAwarded: xp
      }
    });
  }

  const correctNow = await prisma.italianExerciseAttempt.findMany({
    where: { ownerId, lessonId: lesson.id, isCorrect: true },
    select: { exerciseId: true }
  });
  const lessonCompleted = lesson.exercises.every((e) =>
    correctNow.some((r) => r.exerciseId === e.id)
  );

  const existing = await prisma.italianLessonCompletion.findFirst({
    where: { ownerId, lessonId: lesson.id }
  });
  let completionXp = 0;
  let firstCompletion = false;
  if (lessonCompleted) {
    if (existing) {
      completionXp = REPLAY_XP;
      await prisma.italianLessonCompletion.update({
        where: { id: existing.id },
        data: { xpAwarded: existing.xpAwarded + REPLAY_XP }
      });
    } else {
      completionXp = FIRST_COMPLETION_XP;
      firstCompletion = true;
      await prisma.italianLessonCompletion.create({
        data: { ownerId, lessonId: lesson.id, xpAwarded: FIRST_COMPLETION_XP }
      });
    }
  }

  const xpGained = attemptXp + completionXp;
  const signals: Record<string, number> = { italian_xp: xpGained };
  if (firstCompletion) signals.italian_lesson = 1;
  await emitGoalSignals(ownerId, signals);

  const progress = await buildProgress(ownerId);
  const body: ItalianSubmitResult = { results, xpGained, lessonCompleted, progress };
  res.status(201).json(body);
});
```

Note the XP aggregation uses two `aggregate` calls combined with `+`; write them as separate statements to avoid operator-precedence bugs:

```ts
  const attemptAgg = await prisma.italianExerciseAttempt.aggregate({
    where: { ownerId },
    _sum: { xpAwarded: true }
  });
  const completionAgg = await prisma.italianLessonCompletion.aggregate({
    where: { ownerId },
    _sum: { xpAwarded: true }
  });
  const xp = (attemptAgg._sum.xpAwarded ?? 0) + (completionAgg._sum.xpAwarded ?? 0);
```

- [ ] **Step 4: Mount in `server/src/index.ts`** — add import + after the italian line:

```ts
import { italianPathRouter } from "./italianPath";
app.use("/api/italian-path", italianPathRouter);
```

- [ ] **Step 5: Run the tests**

Run: `npm test --workspace server -- src/italianPath.test.ts`
Expected: PASS. If the streak test fails, verify `activeDates` includes an `updatedAt`-derived date for completions (use `existing.updatedAt`/`completedAt` from the DB row instead of `new Date()`): change the completions query to `select: { lessonId: true, xpAwarded: true, completedAt: true }` and push `c.completedAt`.

- [ ] **Step 6: Run the full server suite**

Run: `npm test --workspace server`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add server/src/italianPath.ts server/src/italianPath.test.ts server/src/index.ts
git commit -m "feat(italian): italian-path api with xp, streak, review, goal signals"
```

---

### Task 7: Client API helper + path logic (tested)

**Files:**
- Modify: `client/src/italianApi.ts`
- Create: `client/src/italianPathLogic.ts`
- Test: `client/src/italianPathLogic.test.ts`

**Interfaces:**
- Consumes: Task 1 types; existing `authedFetch` + `json<T>()` pattern in `italianApi.ts`.
- Produces: `getCourse(token): Promise<ItalianCourse>`, `getPathProgress(token): Promise<ItalianPathProgress>`, `submitPathLesson(token, lessonId, answers): Promise<ItalianSubmitResult>`; logic exports `normalizeAnswer`, `gradeExercise`, `isLessonUnlocked`, `nextLessonId`, `lessonStatuses` (used by Tasks 8–9).
- Note: `gradeExercise` here is a deliberate client copy of `server/src/italianPathGrade.ts` (instant feedback, no server round-trip per exercise). Both are unit-tested with the same cases — keep them in sync.

- [ ] **Step 1: Write failing tests — `client/src/italianPathLogic.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { gradeExercise, isLessonUnlocked, lessonStatuses, nextLessonId } from "./italianPathLogic";
import type { ItalianCourse } from "shared";

const course: ItalianCourse = {
  language: "it",
  units: [
    {
      id: "u1",
      title: "Basics",
      lessons: [
        { id: "u1l1", title: "Greetings", exercises: [] },
        { id: "u1l2", title: "Intros", exercises: [] },
        { id: "u1l3", title: "Polite", exercises: [] }
      ]
    }
  ]
};

describe("gradeExercise", () => {
  it("grades choice answers", () => {
    const ex = { id: "e", kind: "choice" as const, direction: "en_to_it" as const, prompt: "hi", choices: ["ciao", "grazie"], answerIndex: 0 };
    expect(gradeExercise(ex, { exerciseId: "e", kind: "choice", choiceIndex: 0 })).toBe(true);
    expect(gradeExercise(ex, { exerciseId: "e", kind: "choice", choiceIndex: 1 })).toBe(false);
  });

  it("grades type answers ignoring case and accents", () => {
    const ex = { id: "e", kind: "type" as const, prompt: "p", accepted: ["caffè"] };
    expect(gradeExercise(ex, { exerciseId: "e", kind: "type", text: "  CAFFE! " })).toBe(true);
    expect(gradeExercise(ex, { exerciseId: "e", kind: "type", text: "te" })).toBe(false);
  });
});

describe("path logic", () => {
  it("unlocks first lesson and completed successors", () => {
    expect(isLessonUnlocked(course, new Set(), "u1l1")).toBe(true);
    expect(isLessonUnlocked(course, new Set(), "u1l2")).toBe(false);
    expect(isLessonUnlocked(course, new Set(["u1l1"]), "u1l2")).toBe(true);
  });

  it("finds the next incomplete lesson", () => {
    expect(nextLessonId(course, new Set())).toBe("u1l1");
    expect(nextLessonId(course, new Set(["u1l1", "u1l2"]))).toBe("u1l3");
    expect(nextLessonId(course, new Set(["u1l1", "u1l2", "u1l3"]))).toBeNull();
  });

  it("computes lesson statuses for the path", () => {
    const s = lessonStatuses(course, new Set(["u1l1"]));
    expect(s.u1l1).toBe("completed");
    expect(s.u1l2).toBe("unlocked");
    expect(s.u1l3).toBe("locked");
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `npm test --workspace client -- src/italianPathLogic.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Create `client/src/italianPathLogic.ts`**

```ts
import type { ItalianAnswer, ItalianCourse, ItalianExercise, ItalianLessonDef } from "shared";

export function normalizeAnswer(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[.,!?;:"'’]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function gradeExercise(ex: ItalianExercise, answer: ItalianAnswer): boolean {
  switch (ex.kind) {
    case "choice":
      return answer.kind === "choice" && answer.choiceIndex === ex.answerIndex;
    case "type":
      return (
        answer.kind === "type" &&
        ex.accepted.some((a) => normalizeAnswer(a) === normalizeAnswer(answer.text))
      );
    case "match": {
      if (answer.kind !== "match") return false;
      const key = (p: { left: string; right: string }) =>
        `${normalizeAnswer(p.left)}|${normalizeAnswer(p.right)}`;
      if (answer.pairs.length !== ex.pairs.length) return false;
      const have = new Set(answer.pairs.map(key));
      return ex.pairs.every((p) => have.has(key(p)));
    }
  }
}

export function flatLessons(course: ItalianCourse): ItalianLessonDef[] {
  return course.units.flatMap((u) => u.lessons);
}

export function isLessonUnlocked(course: ItalianCourse, completed: Set<string>, lessonId: string): boolean {
  const lessons = flatLessons(course);
  const idx = lessons.findIndex((l) => l.id === lessonId);
  if (idx <= 0) return idx === 0;
  return completed.has(lessons[idx - 1].id);
}

export function nextLessonId(course: ItalianCourse, completed: Set<string>): string | null {
  return flatLessons(course).find((l) => !completed.has(l.id))?.id ?? null;
}

export type LessonStatus = "completed" | "unlocked" | "locked";

export function lessonStatuses(course: ItalianCourse, completed: Set<string>): Record<string, LessonStatus> {
  const out: Record<string, LessonStatus> = {};
  for (const l of flatLessons(course)) {
    out[l.id] = completed.has(l.id) ? "completed" : isLessonUnlocked(course, completed, l.id) ? "unlocked" : "locked";
  }
  return out;
}
```

- [ ] **Step 4: Run to verify passing**

Run: `npm test --workspace client -- src/italianPathLogic.test.ts`
Expected: PASS.

- [ ] **Step 5: Append to `client/src/italianApi.ts`** (imports: extend the `shared` import with `ItalianCourse`, `ItalianPathProgress`, `ItalianSubmitResult`, `ItalianAnswer`):

```ts
export async function getCourse(token: string): Promise<ItalianCourse> {
  const res = await authedFetch(token, "/api/italian-path/course");
  return json<ItalianCourse>(res);
}

export async function getPathProgress(token: string): Promise<ItalianPathProgress> {
  const res = await authedFetch(token, "/api/italian-path/progress");
  return json<ItalianPathProgress>(res);
}

export async function submitPathLesson(
  token: string,
  lessonId: string,
  answers: ItalianAnswer[]
): Promise<ItalianSubmitResult> {
  const res = await authedFetch(token, `/api/italian-path/lesson/${lessonId}/submit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ answers })
  });
  return json<ItalianSubmitResult>(res);
}
```

- [ ] **Step 6: Typecheck + tests**

Run: `npm run typecheck` then `npm test --workspace client`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add client/src/italianApi.ts client/src/italianPathLogic.ts client/src/italianPathLogic.test.ts
git commit -m "feat(italian): client path api + local grading/unlock logic"
```

---

### Task 8: Web UI — Path screen + lesson runner

**Files:**
- Create: `client/src/ItalianPath.tsx`
- Create: `client/src/ItalianLessonRunner.tsx`
- Modify: `client/src/ItalianView.tsx`

**Interfaces:**
- Consumes: `getCourse`, `getPathProgress`, `submitPathLesson` (Task 7), logic helpers (Task 7), existing `ItalianLesson` component (unchanged, still the Daily sub-tab).
- Produces: `<ItalianPath token={token} />` (default view of the Italian tab).

- [ ] **Step 1: Create `client/src/ItalianLessonRunner.tsx`**

Session player: props `{ token, lesson, onDone }`. Local state: `idx`, `hearts` (start 5), `answers: ItalianAnswer[]`, `feedback: { correct: boolean; correctText: string } | null`, `matchSel: Record<string, string>` (right→left picks for match exercises), `typeText`, `choicePick`, `submitting`.

Behavior per exercise:
- Render prompt by `ex.kind`. `choice`/`listen`: buttons per choice (for `direction === "listen"`, a "Play 🔊" button speaks `ex.speak` via `speechSynthesis` with `utter.lang = "it-IT"` before choices are shown as answers). `type`: controlled input. `match`: list of Italian lefts, list of shuffled English rights, tap a left then a right to pair.
- "Check" → build the `ItalianAnswer`, grade locally with `gradeExercise`, set `feedback` (correct → green, wrong → red + show `ex.kind === "type" ? ex.accepted[0] : …` correct answer text), decrement `hearts` on wrong.
- "Continue" → push answer, advance `idx`; if `hearts === 0` → finish session early; if `idx === lesson.exercises.length` → finish.
- Finish → `submitPathLesson(token, lesson.id, answers)` (answers include wrong ones — server records them for review), call `onDone(result)`.

```tsx
import { useState } from "react";
import type { ItalianAnswer, ItalianExercise, ItalianLessonDef, ItalianSubmitResult } from "shared";
import { submitPathLesson } from "./italianApi";
import { gradeExercise, normalizeAnswer } from "./italianPathLogic";

interface Props {
  token: string;
  lesson: ItalianLessonDef;
  onDone: (result: ItalianSubmitResult | null) => void;
}

function speakIt(text: string) {
  const u = new SpeechSynthesisUtterance(text);
  u.lang = "it-IT";
  window.speechSynthesis.speak(u);
}

function shuffled<T>(items: T[]): T[] {
  return [...items].sort(() => Math.random() - 0.5);
}

function correctText(ex: ItalianExercise): string {
  if (ex.kind === "type") return ex.accepted[0];
  if (ex.kind === "choice") return ex.choices[ex.answerIndex];
  return ex.pairs.map((p) => `${p.left} = ${p.right}`).join(", ");
}

export default function ItalianLessonRunner({ token, lesson, onDone }: Props) {
  const [idx, setIdx] = useState(0);
  const [hearts, setHearts] = useState(5);
  const [answers, setAnswers] = useState<ItalianAnswer[]>([]);
  const [feedback, setFeedback] = useState<{ correct: boolean } | null>(null);
  const [choicePick, setChoicePick] = useState<number | null>(null);
  const [typeText, setTypeText] = useState("");
  const [matchPicks, setMatchPicks] = useState<{ left: string; right: string }[]>([]);
  const [pickLeft, setPickLeft] = useState<string | null>(null);
  const [rights] = useState<string[]>(() =>
    lesson.exercises[0]?.kind === "match" ? shuffled(lesson.exercises[0].pairs.map((p) => p.right)) : []
  );
  const [submitting, setSubmitting] = useState(false);

  const ex = lesson.exercises[idx];
  const done = idx >= lesson.exercises.length || hearts <= 0;

  async function finish(list: ItalianAnswer[]) {
    setSubmitting(true);
    try {
      const result = await submitPathLesson(token, lesson.id, list);
      onDone(result);
    } catch {
      onDone(null);
    }
  }

  function buildAnswer(): ItalianAnswer {
    if (ex.kind === "choice") return { exerciseId: ex.id, kind: "choice", choiceIndex: choicePick ?? -1 };
    if (ex.kind === "type") return { exerciseId: ex.id, kind: "type", text: typeText };
    return { exerciseId: ex.id, kind: "match", pairs: matchPicks };
  }

  function onCheck() {
    const answer = buildAnswer();
    const correct = gradeExercise(ex, answer);
    setFeedback({ correct });
    if (!correct) setHearts((h) => h - 1);
  }

  function onContinue() {
    const answer = buildAnswer();
    const next = [...answers, answer];
    setAnswers(next);
    setFeedback(null);
    setChoicePick(null);
    setTypeText("");
    setMatchPicks([]);
    setPickLeft(null);
    setIdx((i) => i + 1);
    if (idx + 1 >= lesson.exercises.length || hearts <= 0) void finish(next);
  }

  if (done && !feedback && answers.length === lesson.exercises.length) return null;

  if (submitting) {
    return (
      <div className="border rounded-lg p-6 bg-white text-center">Saving your lesson…</div>
    );
  }

  if (idx >= lesson.exercises.length || (hearts <= 0 && feedback === null && answers.length > 0)) {
    return null;
  }

  return (
    <div className="border rounded-lg bg-white p-6 flex flex-col gap-4">
      <div className="flex justify-between text-sm text-gray-500">
        <span>
          {lesson.title} · {idx + 1}/{lesson.exercises.length}
        </span>
        <span className="text-red-500">{"❤️".repeat(Math.max(hearts, 0))}</span>
      </div>

      <div className="h-2 bg-gray-100 rounded">
        <div
          className="h-2 bg-emerald-500 rounded"
          style={{ width: `${(idx / lesson.exercises.length) * 100}%` }}
        />
      </div>

      <p className="text-lg font-medium">{ex.kind === "choice" ? ex.prompt : ex.kind === "type" ? ex.prompt : "Match the pairs"}</p>

      {ex.kind === "choice" && ex.direction === "listen" && (
        <button
          className="self-start border rounded px-4 py-2 text-2xl"
          onClick={() => speakIt(ex.speak ?? "")}
        >
          🔊 Play
        </button>
      )}

      {ex.kind === "choice" && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {ex.choices.map((c, i) => (
            <button
              key={c}
              onClick={() => !feedback && setChoicePick(i)}
              className={`border rounded px-4 py-3 text-left ${
                choicePick === i ? "border-emerald-600 bg-emerald-50" : "border-gray-300"
              }`}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {ex.kind === "type" && (
        <input
          className="border rounded px-3 py-2"
          value={typeText}
          disabled={!!feedback}
          onChange={(e) => setTypeText(e.target.value)}
          placeholder="Type in Italian…"
        />
      )}

      {ex.kind === "match" && (
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-2">
            {ex.pairs.map((p) => (
              <button
                key={p.left}
                onClick={() => !feedback && setPickLeft(p.left)}
                className={`border rounded px-3 py-2 ${
                  pickLeft === p.left ? "border-emerald-600 bg-emerald-50" : "border-gray-300"
                } ${matchPicks.some((m) => m.left === p.left) ? "opacity-40" : ""}`}
              >
                {p.left}
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-2">
            {shuffled(ex.pairs.map((p) => p.right)).map((r) => (
              <button
                key={r}
                onClick={() => {
                  if (feedback || !pickLeft) return;
                  setMatchPicks((m) => [...m, { left: pickLeft, right: r }]);
                  setPickLeft(null);
                }}
                className="border rounded px-3 py-2 border-gray-300"
              >
                {r}
              </button>
            ))}
          </div>
        </div>
      )}

      {feedback && (
        <div
          className={`rounded px-4 py-3 text-sm ${
            feedback.correct ? "bg-emerald-100 text-emerald-800" : "bg-red-100 text-red-800"
          }`}
        >
          {feedback.correct ? "Correct!" : `Incorrect — correct answer: ${correctText(ex)}`}
        </div>
      )}

      <div className="flex justify-end">
        {!feedback ? (
          <button
            className="bg-emerald-600 text-white rounded px-6 py-2 disabled:opacity-40"
            disabled={
              (ex.kind === "choice" && choicePick === null) ||
              (ex.kind === "type" && !typeText.trim()) ||
              (ex.kind === "match" && matchPicks.length !== ex.pairs.length)
            }
            onClick={onCheck}
          >
            Check
          </button>
        ) : (
          <button className="bg-emerald-600 text-white rounded px-6 py-2" onClick={onContinue}>
            Continue
          </button>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create `client/src/ItalianPath.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import type { ItalianCourse, ItalianPathProgress, ItalianSubmitResult } from "shared";
import { getCourse, getPathProgress } from "./italianApi";
import { lessonStatuses, nextLessonId } from "./italianPathLogic";
import ItalianLessonRunner from "./ItalianLessonRunner";

interface Props {
  token: string;
}

export default function ItalianPath({ token }: Props) {
  const [course, setCourse] = useState<ItalianCourse | null>(null);
  const [progress, setProgress] = useState<ItalianPathProgress | null>(null);
  const [activeLessonId, setActiveLessonId] = useState<string | null>(null);
  const [result, setResult] = useState<ItalianSubmitResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [c, p] = await Promise.all([getCourse(token), getPathProgress(token)]);
      setCourse(c);
      setProgress(p);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to load");
    }
  }, [token]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (error) {
    return (
      <div className="border rounded-lg p-6 bg-red-50 text-red-700 flex justify-between items-center">
        <span>{error}</span>
        <button className="border rounded px-3 py-1" onClick={() => void refresh()}>
          Retry
        </button>
      </div>
    );
  }
  if (!course || !progress) return <div className="p-6 text-gray-500">Loading…</div>;

  const completed = new Set(progress.completedLessonIds);
  const statuses = lessonStatuses(course, completed);
  const activeLesson = course.units
    .flatMap((u) => u.lessons)
    .find((l) => l.id === activeLessonId);

  if (activeLesson) {
    return (
      <ItalianLessonRunner
        token={token}
        lesson={activeLesson}
        onDone={(r) => {
          setActiveLessonId(null);
          setResult(r);
          void refresh();
        }}
      />
    );
  }

  const nextId = nextLessonId(course, completed);

  return (
    <div className="flex flex-col gap-5">
      <div className="border rounded-lg bg-white p-4 flex flex-wrap gap-6 text-sm">
        <span>
          <b>{progress.xp}</b> XP
        </span>
        <span>
          <b>{progress.streak}</b> day streak
        </span>
        <span>
          <b>{completed.size}</b>/{progress.totalLessons} lessons
        </span>
        {progress.reviewDue.length > 0 && (
          <span className="text-amber-600">
            {progress.reviewDue.length} mistake{progress.reviewDue.length > 1 ? "s" : ""} to review
          </span>
        )}
      </div>

      {result && (
        <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-800">
          {result.lessonCompleted ? "Lesson complete! " : ""}
          +{result.xpGained} XP{result.lessonCompleted ? " 🎉" : ""}
        </div>
      )}

      {nextId && (
        <button
          className="bg-emerald-600 text-white rounded-lg px-6 py-3 font-medium self-start"
          onClick={() => {
            setResult(null);
            setActiveLessonId(nextId);
          }}
        >
          Continue: Next lesson
        </button>
      )}

      {course.units.map((unit) => {
        const unitDone = unit.lessons.filter((l) => completed.has(l.id)).length;
        return (
          <div key={unit.id} className="flex flex-col gap-3">
            <div className="flex justify-between items-baseline border-b pb-1">
              <h3 className="font-semibold">{unit.title}</h3>
              <span className="text-xs text-gray-500">
                {unitDone}/{unit.lessons.length}
              </span>
            </div>
            <div className="flex flex-wrap gap-3">
              {unit.lessons.map((l) => {
                const status = statuses[l.id];
                return (
                  <button
                    key={l.id}
                    disabled={status === "locked"}
                    onClick={() => {
                      setResult(null);
                      setActiveLessonId(l.id);
                    }}
                    className={`rounded-full w-36 h-36 flex flex-col items-center justify-center gap-1 border-4 text-sm ${
                      status === "completed"
                        ? "border-yellow-400 bg-yellow-50"
                        : status === "unlocked"
                          ? "border-emerald-500 bg-emerald-50"
                          : "border-gray-300 bg-gray-50 text-gray-400"
                    }`}
                  >
                    <span className="text-2xl">
                      {status === "completed" ? "⭐" : status === "unlocked" ? "▶" : "🔒"}
                    </span>
                    <span className="px-2 text-center font-medium">{l.title}</span>
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 3: Rewrite `client/src/ItalianView.tsx`**

```tsx
import { useEffect, useState } from "react";
import ItalianLesson from "./ItalianLesson";
import ItalianPath from "./ItalianPath";
import { getProgress } from "./italianApi";

interface Props {
  token: string;
}

const TABS = [
  { key: "path", label: "Path" },
  { key: "daily", label: "Daily Lesson" }
] as const;

type TabKey = (typeof TABS)[number]["key"];

export default function ItalianView({ token }: Props) {
  const [tab, setTab] = useState<TabKey>("path");
  const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

  useEffect(() => {
    getProgress(token)
      .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
      .catch(() => setProgress(null));
  }, [token]);

  return (
    <div className="w-full max-w-3xl flex flex-col gap-4">
      <div className="flex gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-t font-medium ${
              tab === t.key ? "bg-emerald-600 text-white" : "text-gray-500 hover:text-gray-800"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "path" ? (
        <ItalianPath token={token} />
      ) : (
        <ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />
      )}
    </div>
  );
}
```

- [ ] **Step 4: Typecheck + tests**

Run: `npm run typecheck` and `npm test --workspace client`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/src/ItalianPath.tsx client/src/ItalianLessonRunner.tsx client/src/ItalianView.tsx
git commit -m "feat(italian): web path screen and lesson runner"
```

---

### Task 9: Goals tab integration (web)

**Files:**
- Create: `client/src/ItalianGoalCard.tsx`
- Modify: `client/src/Goals.tsx` (line 10 `AUTOSOURCE_OPTIONS`; import; render above the goals list)

**Interfaces:**
- Consumes: `getPathProgress` (Task 7), existing `Props { token }` in `Goals.tsx`.

- [ ] **Step 1: Create `client/src/ItalianGoalCard.tsx`**

```tsx
import { useEffect, useState } from "react";
import type { ItalianPathProgress } from "shared";
import { getPathProgress } from "./italianApi";

interface Props {
  token: string;
}

export default function ItalianGoalCard({ token }: Props) {
  const [progress, setProgress] = useState<ItalianPathProgress | null>(null);

  useEffect(() => {
    getPathProgress(token)
      .then(setProgress)
      .catch(() => setProgress(null));
  }, [token]);

  if (!progress) return null;

  const pct = progress.totalLessons > 0 ? Math.round((progress.completedLessonIds.length / progress.totalLessons) * 100) : 0;

  return (
    <div className="border rounded-lg bg-white p-4 flex flex-col gap-2">
      <div className="flex justify-between text-sm font-medium">
        <span>🇮🇹 Italian path</span>
        <span className="text-gray-500">
          {progress.xp} XP · {progress.streak} day streak
        </span>
      </div>
      <div className="h-2 bg-gray-100 rounded">
        <div className="h-2 bg-emerald-500 rounded" style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500">
        {progress.completedLessonIds.length}/{progress.totalLessons} lessons ({pct}%)
        {progress.reviewDue.length > 0 ? ` · ${progress.reviewDue.length} to review` : ""}
      </span>
    </div>
  );
}
```

- [ ] **Step 2: Modify `client/src/Goals.tsx`**

1. Line 10: `const AUTOSOURCE_OPTIONS = ["none", "workout", "italian_words", "italian_lesson", "italian_xp", "study"] as const;`
2. Add import: `import ItalianGoalCard from "./ItalianGoalCard";`
3. In the JSX, render `<ItalianGoalCard token={token} />` as the first element inside the goals container (just above the goals list / heading), so it reads as an at-a-glance card.

- [ ] **Step 3: Typecheck + tests**

Run: `npm run typecheck`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add client/src/ItalianGoalCard.tsx client/src/Goals.tsx
git commit -m "feat(goals): italian path card and italian_xp autosource"
```

---

### Task 10: Web verification gate

**Files:** none (verification only).

- [ ] **Step 1: Full test suite + typecheck**

Run: `npm run typecheck` and `npm test --workspace server` and `npm test --workspace client`
Expected: all PASS.

- [ ] **Step 2: Manual smoke (dev servers)**

Run: `npm run dev` (root, or dev for server + client in two shells), log in, open Italian tab → Path tab shows 3 units; start lesson 1; answer exercises; hearts drop on wrong answers; finish → XP header updates; Goals tab → Italian card shows XP/streak/lessons; create a goal with autoSource `italian_xp` and confirm `thisWeekCount` grows after a lesson.
Expected: all behaviors work. Fix any issues before proceeding.

- [ ] **Step 3: Commit fixes if any**

```bash
git add -A
git commit -m "fix(italian): web smoke fixes"
```

(Skip if no fixes needed.)

---

### Task 11: Android DTOs, grading, API + Authorization header

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/daytoday/network/ItalianDtos.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/daytoday/network/DayTodayApi.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/network/DayTodayApiImpl.kt`

**Interfaces:**
- Consumes: server JSON shapes from Task 1 (field names must match exactly — `kotlinx.serialization` with `@SerialName` where needed; the server uses default JSON field names).
- Produces: DTOs `ItalianCourseDto`, `ItalianLessonDto`, `ItalianExerciseDto` (sealed), `ItalianAnswerDto` (sealed), `ItalianPathProgressDto`, `ItalianSubmitResultDto`; API methods `getItalianCourse(authToken)`, `getItalianProgress(authToken)`, `submitItalianLesson(authToken, lessonId, answers)`; `object ItalianGrading { fun normalize(s): String; fun grade(ex, answer): Boolean }`. Consumed by Tasks 12 and 14.

- [ ] **Step 1: Create `composeApp/src/commonMain/kotlin/com/daytoday/network/ItalianDtos.kt`**

Mirror the TS types; polymorphic kinds use `@JsonTypeInfo`/`@JsonSubTypes` for exercises and answers (server sends no type discriminator field other than `kind`):

```kotlin
package com.daytoday.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class ItalianPairDto(val left: String, val right: String)

@Serializable
@JsonClassDiscriminator("kind")
sealed interface ItalianExerciseDto {
    val id: String

    @Serializable
    @SerialName("choice")
    data class Choice(
        override val id: String,
        val direction: String,
        val prompt: String,
        val choices: List<String>,
        val answerIndex: Int,
        val speak: String? = null,
    ) : ItalianExerciseDto

    @Serializable
    @SerialName("type")
    data class Type(
        override val id: String,
        val prompt: String,
        val accepted: List<String>,
    ) : ItalianExerciseDto

    @Serializable
    @SerialName("match")
    data class Match(
        override val id: String,
        val pairs: List<ItalianPairDto>,
    ) : ItalianExerciseDto
}

@Serializable
data class ItalianLessonDto(val id: String, val title: String, val exercises: List<ItalianExerciseDto>)

@Serializable
data class ItalianUnitDto(val id: String, val title: String, val lessons: List<ItalianLessonDto>)

@Serializable
data class ItalianCourseDto(val language: String, val units: List<ItalianUnitDto>)

@Serializable
@JsonClassDiscriminator("kind")
sealed interface ItalianAnswerDto {
    val exerciseId: String

    @Serializable
    @SerialName("choice")
    data class Choice(override val exerciseId: String, val choiceIndex: Int) : ItalianAnswerDto

    @Serializable
    @SerialName("type")
    data class Type(override val exerciseId: String, val text: String) : ItalianAnswerDto

    @Serializable
    @SerialName("match")
    data class Match(override val exerciseId: String, val pairs: List<ItalianPairDto>) : ItalianAnswerDto
}

@Serializable
data class ItalianReviewDueDto(val lessonId: String, val exerciseId: String)

@Serializable
data class ItalianPathProgressDto(
    val xp: Int,
    val streak: Int,
    val completedLessonIds: List<String>,
    val totalLessons: Int,
    val reviewDue: List<ItalianReviewDueDto>,
)

@Serializable
data class ItalianSubmitResultDto(
    val results: List<ItalianExerciseResultDto>,
    val xpGained: Int,
    val lessonCompleted: Boolean,
    val progress: ItalianPathProgressDto,
)

@Serializable
data class ItalianExerciseResultDto(val exerciseId: String, val isCorrect: Boolean)

@Serializable
data class ItalianSubmitRequest(val answers: List<ItalianAnswerDto>)

object ItalianGrading {
    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[.,!?;:\"'’]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun grade(ex: ItalianExerciseDto, answer: ItalianAnswerDto): Boolean = when (ex) {
        is ItalianExerciseDto.Choice ->
            answer is ItalianAnswerDto.Choice && answer.choiceIndex == ex.answerIndex
        is ItalianExerciseDto.Type ->
            answer is ItalianAnswerDto.Type && ex.accepted.any { normalize(it) == normalize(answer.text) }
        is ItalianExerciseDto.Match -> {
            if (answer !is ItalianAnswerDto.Match) false
            else {
                if (answer.pairs.size != ex.pairs.size) false
                else {
                    val key = { p: ItalianPairDto -> "${normalize(p.left)}|${normalize(p.right)}" }
                    val have = answer.pairs.map(key).toSet()
                    ex.pairs.all { have.contains(key(it)) }
                }
            }
        }
    }
}
```

Note: Unicode combining marks — `Regex("\\p{M}")` after `lowercase()` strips accents (`caffè` → `caffe`), matching the server's NFD strip. If `\\p{M}` misbehaves in a test, use `Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")` (`java.text.Normalizer`, available on Android).

- [ ] **Step 2: Add methods to `DayTodayApi.kt`** (append before the closing brace; keep existing methods untouched):

```kotlin
suspend fun getItalianCourse(authToken: String): ItalianCourseDto
suspend fun getItalianProgress(authToken: String): ItalianPathProgressDto
suspend fun submitItalianLesson(authToken: String, lessonId: String, answers: List<ItalianAnswerDto>): ItalianSubmitResultDto
```

- [ ] **Step 3: Implement in `DayTodayApiImpl.kt`**

Find how existing POSTs build requests (e.g. `saveSession`) and mirror it; the pattern for GET with header:

```kotlin
override suspend fun getItalianCourse(authToken: String): ItalianCourseDto =
    client.get(absoluteUrl("api/italian-path/course")) {
        header("Authorization", "Bearer $authToken")
    }.body()

override suspend fun getItalianProgress(authToken: String): ItalianPathProgressDto =
    client.get(absoluteUrl("api/italian-path/progress")) {
        header("Authorization", "Bearer $authToken")
    }.body()

override suspend fun submitItalianLesson(
    authToken: String,
    lessonId: String,
    answers: List<ItalianAnswerDto>,
): ItalianSubmitResultDto =
    client.post(absoluteUrl("api/italian-path/lesson/$lessonId/submit")) {
        header("Authorization", "Bearer $authToken")
        contentType(ContentType.Application.Json)
        setBody(ItalianSubmitRequest(answers))
    }.body()
```

Add imports as needed: `io.ktor.http.ContentType`, `io.ktor.http.contentType`, `io.ktor.client.request.header`, `io.ktor.client.call.body`. The client already installs `ContentNegotiation` with `networkJson` — verify the json config in `DayTodayApiImpl` uses `ignoreUnknownKeys = true` (it does per `Dtos.kt` `networkJson`); sealed-class polymorphism requires `classDiscriminator = "kind"` — `networkJson` may need `. { classDiscriminator = "kind" }`; adjust `networkJson` in `Dtos.kt` **only if** the existing config lacks it, keeping `ignoreUnknownKeys = true`.

- [ ] **Step 4: Do NOT run gradle.** Type-level verification happens in Task 15.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/daytoday/network/ItalianDtos.kt composeApp/src/commonMain/kotlin/com/daytoday/network/DayTodayApi.kt composeApp/src/androidMain/kotlin/com/daytoday/network/DayTodayApiImpl.kt
git commit -m "feat(italian): android DTOs, grading, api with auth header"
```

---

### Task 12: Android goal type + Italian goal tracker

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/settings/GoalEntry.kt`
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/settings/ItalianGoals.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/goals/GoalsViewModel.kt`

**Interfaces:**
- Consumes: `SettingsManager.goals: Flow<List<GoalEntry>>`, `SettingsManager.setGoals` (existing).
- Produces: `GoalType.ITALIAN_LESSONS`; top-level helpers `fun italianWeekStart(): String`, `fun italianWeeklyReset(entry, anchor): GoalEntry` in `settings` (renamed to avoid clashing with `GoalsViewModel` privates during transition); `suspend fun recordItalianLessonCompleted(settingsManager: SettingsManager)`. Consumed by Task 14.

- [ ] **Step 1: `GoalEntry.kt` — add the enum entry before `CUSTOM`:**

```kotlin
    ITALIAN_LESSONS(
        id = "italian_lessons",
        label = "Italian lessons",
        unit = "lessons",
        defaultValue = 3.0,
        maxValue = 30,
        source = StatsSource.NONE,
    ),
```

Also add top-level helpers at file bottom:

```kotlin
fun italianWeekStart(): String =
    java.time.LocalDate.now()
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
        .toString()

fun italianWeeklyReset(entry: GoalEntry, anchor: String): GoalEntry {
    if (entry.type != GoalType.ITALIAN_LESSONS || entry.period != GoalPeriod.WEEK) return entry
    return when {
        entry.lastResetWeekStart.isEmpty() -> entry.copy(lastResetWeekStart = anchor)
        entry.lastResetWeekStart != anchor -> entry.copy(progress = 0.0, lastResetWeekStart = anchor)
        else -> entry
    }
}
```

- [ ] **Step 2: Create `settings/ItalianGoals.kt`**

```kotlin
package com.daytoday.settings

import kotlinx.coroutines.flow.first

suspend fun recordItalianLessonCompleted(settingsManager: SettingsManager) {
    val anchor = italianWeekStart()
    val entries = settingsManager.goals.first().map { italianWeeklyReset(it, anchor) }
    val target = entries.firstOrNull { it.type == GoalType.ITALIAN_LESSONS }
    val updated = if (target != null) {
        entries.map {
            if (it.id == target.id) it.copy(progress = it.progress + 1.0) else it
        }
    } else {
        entries + GoalEntry(
            id = generateGoalId(GoalType.ITALIAN_LESSONS, entries.map { e -> e.id }),
            type = GoalType.ITALIAN_LESSONS,
            target = GoalType.ITALIAN_LESSONS.defaultValue,
            period = GoalPeriod.WEEK,
            progress = 1.0,
            lastResetWeekStart = anchor,
        )
    }
    settingsManager.setGoals(updated)
}
```

- [ ] **Step 3: `GoalsViewModel.kt` — make the new type render and reset without changing existing behavior**

1. In `applyWeeklyResets` / `weeklyReset`, allow the new type. Replace the guard line in `weeklyReset`:

```kotlin
    if (entry.type != GoalType.CUSTOM && entry.type != GoalType.ITALIAN_LESSONS) return entry
    if (entry.type == GoalType.ITALIAN_LESSONS && entry.period != GoalPeriod.WEEK) return entry
```

2. In `refresh()`, replace `current = currentValue(entry.type, day, week)` with:

```kotlin
current = if (entry.type == GoalType.ITALIAN_LESSONS || entry.type == GoalType.CUSTOM) {
    entry.progress
} else {
    currentValue(entry.type, day, week)
}
```

3. In `currentValue`'s `when`, add before `GoalType.CUSTOM`:

```kotlin
            GoalType.ITALIAN_LESSONS -> entry.progress
```

— since `currentValue` only receives `type`, instead handle it purely via step 2 (do not touch `currentValue`; exhaustive-when over enum will force a branch when the enum grows — if the compiler demands a branch, add `GoalType.ITALIAN_LESSONS -> 0.0` there and keep step 2 as the real value path).

4. Verify `GoalsScreen` renders entries by `entry.type`: stats-driven rows use `item.current` (line 164/180) — since step 2 passes `entry.progress` as `current`, the standard `GoalRow` renders it with no `GoalsScreen.kt` changes. CUSTOM rows (line 195) stay untouched.

- [ ] **Step 4: Sanity check existing goal behavior is unchanged** — the only edits are enum addition, an extra allowed type in reset, and a value override for the new type. Re-read your diff: no CUSTOM or stats-source behavior may change.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/daytoday/settings/GoalEntry.kt composeApp/src/androidMain/kotlin/com/daytoday/settings/ItalianGoals.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/goals/GoalsViewModel.kt
git commit -m "feat(italian): android italian_lessons goal type + tracker"
```

---

### Task 13: Android navigation (Italian tab)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/NavHost.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/BottomNavHost.kt`

**Interfaces:**
- Produces: `Screen.Italian` route `"Italian"`; NavHost entry rendering `ItalianScreen()` (created in Task 14 — import it there and expect `fun ItalianScreen()` with no required params). Consumed by Task 14.

- [ ] **Step 1: `Screen.kt` — add before the closing brace:**

```kotlin
    data object Italian : Screen {
        override val route: String = "Italian"
    }
```

- [ ] **Step 2: `NavHost.kt` — add import + entry:**

```kotlin
import com.daytoday.ui.screen.italian.ItalianScreen
```

```kotlin
        composable(Screen.Italian.route) {
            ItalianScreen()
        }
```

- [ ] **Step 3: `BottomNavHost.kt` — add import + item** (after Goals, line 37):

```kotlin
import androidx.compose.material.icons.filled.MenuBook
```

```kotlin
        BottomNavItem(Screen.Italian, Icons.Default.MenuBook, "Italian"),
```

`material-icons-extended` is already a dependency (`composeApp/build.gradle.kts:36`), so `MenuBook` resolves. With 7 items the bar is crowded — if labels clip, set `label = { Text(item.label, fontSize = 10.sp) }` only if needed after visual check; do not preemptively restyle.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/NavHost.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/BottomNavHost.kt
git commit -m "feat(italian): android italian bottom-nav tab"
```

---

### Task 14: Android Italian screen + ViewModel + exercise views

**Files (create only — do not touch any other file):**
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ItalianViewModel.kt`
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ItalianScreen.kt`
- `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ExerciseViews.kt`

**Interfaces:**
- Consumes: `DayTodayApi` methods (Task 11), `ItalianGrading` (Task 11), `SettingsManager.authToken`, `recordItalianLessonCompleted` (Task 12), `Screen.Italian` (Task 13), existing `UiState` (`com.daytoday.ui.screen.workout.UiState`), shared components `DayTodayTopAppBar`, `DayTodayCard`, `DayTodayButton`, `ErrorState`, `LoadingOverlay` from `com.daytoday.ui.theme.Components`.
- Produces: `@HiltViewModel class ItalianViewModel @Inject constructor(settingsManager: SettingsManager, api: DayTodayApi)`, `@Composable fun ItalianScreen(viewModel: ItalianViewModel = hiltViewModel())`.

- [ ] **Step 1: `ItalianViewModel.kt`**

```kotlin
package com.daytoday.ui.screen.italian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.network.DayTodayApi
import com.daytoday.network.ItalianAnswerDto
import com.daytoday.network.ItalianCourseDto
import com.daytoday.network.ItalianExerciseDto
import com.daytoday.network.ItalianGrading
import com.daytoday.network.ItalianPathProgressDto
import com.daytoday.network.ItalianSubmitResultDto
import com.daytoday.network.ItalianLessonDto
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.recordItalianLessonCompleted
import com.daytoday.ui.screen.workout.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LessonSession(
    val lesson: ItalianLessonDto,
    val index: Int = 0,
    val hearts: Int = 5,
    val answers: List<ItalianAnswerDto> = emptyList(),
    val feedbackCorrect: Boolean? = null,
    val submitting: Boolean = false,
)

data class ItalianUiState(
    val course: ItalianCourseDto? = null,
    val progress: ItalianPathProgressDto? = null,
    val session: LessonSession? = null,
    val lastResult: ItalianSubmitResultDto? = null,
)

@HiltViewModel
class ItalianViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val api: DayTodayApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ItalianUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<ItalianUiState>> = _uiState

    private var state = ItalianUiState()

    init {
        refresh()
    }

    private fun setState(transform: (ItalianUiState) -> ItalianUiState) {
        state = transform(state)
        _uiState.value = UiState.Success(state)
    }

    fun refresh() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val course = api.getItalianCourse(token)
                val progress = api.getItalianProgress(token)
                state = ItalianUiState(course = course, progress = progress)
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unable to load Italian course")
            }
        }
    }

    fun startLesson(lessonId: String) {
        val lesson = state.course?.units?.flatMap { it.lessons }?.find { it.id == lessonId } ?: return
        setState { it.copy(session = LessonSession(lesson = lesson), lastResult = null) }
    }

    fun exitSession() {
        setState { it.copy(session = null) }
    }

    fun currentExercise(): ItalianExerciseDto? =
        state.session?.lesson?.exercises?.getOrNull(state.session.index)

    fun submitCurrent(answer: ItalianAnswerDto) {
        val session = state.session ?: return
        val ex = session.lesson.exercises.getOrNull(session.index) ?: return
        if (session.feedbackCorrect != null) return
        val correct = ItalianGrading.grade(ex, answer)
        val hearts = if (correct) session.hearts else session.hearts - 1
        setState {
            it.copy(
                session = session.copy(
                    answers = session.answers + answer,
                    feedbackCorrect = correct,
                    hearts = hearts,
                )
            )
        }
    }

    fun continueSession() {
        val session = state.session ?: return
        val finished = session.index + 1 >= session.lesson.exercises.length || session.hearts <= 0
        if (finished) {
            finishSession(session)
        } else {
            setState { it.copy(session = session.copy(index = session.index + 1, feedbackCorrect = null)) }
        }
    }

    private fun finishSession(session: LessonSession) {
        setState { it.copy(session = session.copy(submitting = true)) }
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val result = api.submitItalianLesson(token, session.lesson.id, session.answers)
                if (result.lessonCompleted) {
                    recordItalianLessonCompleted(settingsManager)
                }
                state = state.copy(
                    session = null,
                    lastResult = result,
                    progress = result.progress,
                )
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to save lesson")
            }
        }
    }
}
```

- [ ] **Step 2: `ExerciseViews.kt`** — composables for each exercise kind plus a `LessonRunner`:

```kotlin
package com.daytoday.ui.screen.italian

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daytoday.network.ItalianAnswerDto
import com.daytoday.network.ItalianExerciseDto
import java.util.Locale

@Composable
fun rememberItalianTts(): TextToSpeech? {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }
    return tts
}
```

Fix the init ordering while implementing: create the `TextToSpeech` instance into the `tts` state properly:

```kotlin
@Composable
fun rememberItalianTts(): TextToSpeech {
    val context = LocalContext.current
    val tts = remember {
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) created?.language = Locale.ITALIAN
        }
        created
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    return tts
}
```

Then `ExerciseContent(ex, answer: ItalianAnswerDto?, onAnswer: (ItalianAnswerDto) -> Unit, locked: Boolean)`:
- `Choice`: `Column` of `OutlinedButton`s over `ex.choices`; `listen` direction adds a "🔊 Play" `Button` that calls `tts.speak(ex.speak, TextToSpeech.QUEUE_FLUSH, null, "italian")`; selecting calls `onAnswer(ItalianAnswerDto.Choice(ex.id, i))`.
- `Type`: `OutlinedTextField` + submit button → `ItalianAnswerDto.Type(ex.id, text)`.
- `Match`: two columns; tap left then right → accumulate pairs into local state → `onAnswer(ItalianAnswerDto.Match(ex.id, pairs))` when all paired.

`LessonRunner(session, onAnswer, onContinue, onQuit)`: shows progress `LinearProgressIndicator`, hearts (repeat "❤️"), prompt, `ExerciseContent` (locked when `session.feedbackCorrect != null`), feedback banner (green/red + correct answer text for wrong), Check/Continue buttons wired to `onAnswer`/`onContinue`.

- [ ] **Step 3: `ItalianScreen.kt`**

```kotlin
package com.daytoday.ui.screen.italian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daytoday.network.ItalianCourseDto
import com.daytoday.network.ItalianLessonDto
import com.daytoday.network.ItalianPathProgressDto
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.theme.Components.DayTodayTopAppBar
import com.daytoday.ui.theme.Components.ErrorState
import com.daytoday.ui.theme.Components.LoadingOverlay

@Composable
fun ItalianScreen(viewModel: ItalianViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.material3.Scaffold(
        topBar = { DayTodayTopAppBar(title = "Italian") }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (val state = uiState) {
                is UiState.Loading -> LoadingOverlay()
                is UiState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                is UiState.Success -> {
                    val session = state.data.session
                    if (session != null) {
                        LessonRunner(
                            session = session,
                            onAnswer = viewModel::submitCurrent,
                            onContinue = viewModel::continueSession,
                            onQuit = viewModel::exitSession,
                        )
                    } else {
                        PathContent(
                            state = state.data,
                            onLessonClick = viewModel::startLesson,
                        )
                    }
                }
            }
        }
    }
}
```

`PathContent`: header `Card` with `progress.xp` XP, `progress.streak` day streak, `completed/total lessons` lessons; result banner if `lastResult != null` ("Lesson complete! +N XP"); "Continue: Next lesson" button (first incomplete unlocked lesson — compute from `course` + `completedLessonIds` with the same linear rule); then per unit a titled `Column` of circular lesson nodes (`Surface(shape = CircleShape, color = completed → Color(0xFFFFF3B0) / unlocked → Color(0xFFD1FAE5) / locked → Color(0xFFF3F4F6))` with title inside, `enabled = unlocked`, `onClick = { onLessonClick(l.id) }`).

Use `collectAsStateWithLifecycle` (import `androidx.lifecycle.compose.collectAsStateWithLifecycle`) — matches other screens; verify the import used in `GoalsScreen.kt` and copy it exactly.

- [ ] **Step 4: Commit (do NOT compile here)**

```bash
git add composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian
git commit -m "feat(italian): android path screen, session runner, exercise views"
```

---

### Task 15: Final verification (executor: plan owner, NOT a subagent)

**Files:** fixes only.

- [ ] **Step 1: Server + client**

Run: `npm run typecheck`, `npm test --workspace server`, `npm test --workspace client`
Expected: all PASS. Fix regressions.

- [ ] **Step 2: Android compile gate** (run once, never in parallel with another gradle run)

```bat
gradlew.bat :composeApp:compileDebugKotlinAndroid > build_check.txt 2>&1
findstr /b "e:" build_check.txt
```

Expected: no `e:` error lines / `BUILD SUCCESSFUL`. Fix all reported errors (likely candidates: missing imports, `collectAsStateWithLifecycle` import, json `classDiscriminator`, exhaustive `when` in `GoalsViewModel.currentValue`, `TextToSpeech` init) and re-run until green.

- [ ] **Step 3: Install on device (optional if device connected)**

```bat
gradlew.bat :composeApp:installDebug
```

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore(italian): fix compile issues from duolingo feature integration"
```

(Skip if nothing to fix.)

---

## Self-Review Notes

- Spec coverage: path+exercises+XP+streak+hearts+mistake review → Tasks 4–8 (hearts = client session state in both runners); Goals web link (`italian_lesson` + `italian_xp` + card) → Tasks 3, 6, 9; Android parity → Tasks 11–14; hand-authored content → Task 4; existing LLM Daily Lesson preserved → Task 8 sub-tab.
- The `progress.xp` aggregation in Task 6 must use two separate `aggregate` calls (shown in the follow-up block) — do not inline them with `??` + `+`.
- Client/server grading are intentionally duplicated (client copy in `italianPathLogic.ts`, server copy in `italianPathGrade.ts`, Kotlin copy in `ItalianGrading`) — all three share the same normalization rules; server is authoritative on submit.
