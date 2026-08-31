# Phase 4 — Italian Tutor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Add an Italian tutor module — a daily generated lesson plus voice practice (browser-native STT + TTS) with LLM-driven corrections and a streak tracker.

**Architecture:** The server generates and caches a "lesson of the day" via the canonical `LLMProvider` and exposes `check`/`translate` endpoints that route the learner's transcribed attempts through the same provider and record practice logs for progress. The client PWA renders today's lesson, captures the user's voice with the Web Speech API, speaks Italian back with `speechSynthesis`, and shows structured, retryable corrections. The provider path stays free: Phase 3's `http` provider with an optional free-tier key, and a deterministic canned fallback whenever the provider throws or its output doesn't parse. Voice quality is browser-native — "decent, not Siri-grade" per the spec.

**Tech Stack:** Web Speech API (`SpeechRecognition`/`webkitSpeechRecognition`), `speechSynthesis`, Express, Prisma (SQLite), vitest + supertest, React 18, TypeScript, shared workspace types, `LLMProvider`.

> **Cross-module hook (weekly goals):** When the user completes an Italian lesson or learns new words (Phase 4 practice/lesson completion), if they have a `WeeklyGoal` with `autoSource: "italian_lesson"` (or `"italian_words"`, e.g. "learn 10 new words in Italian"), insert matching `GoalEvent` rows so the weekly-goal progress auto-increments. Do this in the relevant Phase 4 practice/lesson-complete route.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; lint + typecheck must pass before any commit.
- Single-user app; all rows carry `ownerId`.
- Authed routes use `requireAuth` from `server/src/session.ts`.
- Voice uses browser-native Web Speech API (STT) + `speechSynthesis` (TTS); LLM for responses/corrections via `LLMProvider` at `server/src/providers/llm.ts` (default free-tier, no paid keys). Quality is "decent, not Siri-grade" per the spec — accept this.
- Windows host (cmd.exe); paths use `\` in commands.

---

### Task 4.1: LLMProvider (canonical) + Italian lesson endpoint

> **Context for this task:** `server/src/providers/llm.ts` already exists from Phase 3 (Task 3.4) with `LLMProvider { generateFlashcards; generateQuiz }`, `llmHttpProvider()`, `llmMockProvider()`, and `getLLMProvider()` switching on `LLM_PROVIDER` (`"http"` default, `"mock"` for tests). That plan explicitly left it "used by Phase 4 later." This task EXTENDS that canonical file with a generic `complete(prompt)` method — used by the Italian module — and keeps the Phase 3 API and env keys intact. Do not recreate the file, do not change `LLM_PROVIDER`/`LLM_HTTP_KEY` semantics.

**Files:**
- Modify: `shared/src/index.ts` (append Italian lesson types)
- Modify: `server/prisma/schema.prisma` (Lesson + PracticeLog models)
- Modify: `server/src/providers/llm.ts` (extend interface with `complete`; add `extractJson`)
- Modify: `server/src/providers/llm.test.ts` (append `complete` tests; Phase 3 created this file)
- Create: `server/src/italian.ts`
- Test: `server/src/italian.test.ts`
- Modify: `server/src/index.ts` (mount router)

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`; the Phase 3 `LLMProvider` (`llmHttpProvider`, `llmMockProvider`, `getLLMProvider`) from `server/src/providers/llm.ts`.
- Produces:
  - `LLMProvider` extended with `complete(prompt: string): Promise<string>` (existing `generateFlashcards`/`generateQuiz` unchanged)
  - `extractJson(text: string): Record<string, unknown> | null` (exported from `llm.ts`)
  - `dateKeyFor(d: Date): string` (local `YYYY-MM-DD`)
  - `interface LessonBody { title: string; tip: string; vocab: ItalianVocabItem[]; phrases: ItalianPhrase[] }`
  - `fallbackLessonBody(): LessonBody`, `lessonPrompt(dateKey: string): string`, `tryParseLesson(text: string): LessonBody | null`, `generateLessonBody(provider: LLMProvider, dateKey: string): Promise<LessonBody>` (falls back to canned content on throw or unparseable output)
  - `createItalianRouter(provider?: LLMProvider): Router`; `export const italianRouter = createItalianRouter()`
  - Route `GET /lesson/today?date=YYYY-MM-DD&force=1` → `ItalianLesson`
  - Shared types `ItalianVocabItem`, `ItalianPhrase`, `ItalianLesson`
  - Prisma models `Lesson` and `PracticeLog`

- [ ] **Step 1: Append the Italian lesson types to shared**

In `shared/src/index.ts` append:
```ts
export interface ItalianVocabItem {
  english: string;
  italian: string;
}

export interface ItalianPhrase {
  english: string;
  italian: string;
}

export interface ItalianLesson {
  id: string;
  date: string; // YYYY-MM-DD
  title: string;
  tip: string;
  vocab: ItalianVocabItem[];
  phrases: ItalianPhrase[];
}
```

- [ ] **Step 2: Add the Lesson and PracticeLog models to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Lesson {
  id          String    @id @default(cuid())
  ownerId     String
  date        String
  title       String
  tip         String
  vocab       String
  phrases     String
  completedAt DateTime?
  createdAt   DateTime  @default(now())

  @@unique([ownerId, date])
  @@index([ownerId])
}

model PracticeLog {
  id          String   @id @default(cuid())
  ownerId     String
  lessonDate  String
  phraseIndex Int
  isCorrect   Boolean
  userAttempt String
  createdAt   DateTime @default(now())

  @@index([ownerId, lessonDate])
}
```
`vocab` and `phrases` are JSON strings (SQLite has no native array). Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: Prisma client regenerated; `dev.db` updated; exit 0.

- [ ] **Step 3: Write the failing `complete` tests (appended to the existing llm test file)**

Append to `server/src/providers/llm.test.ts` (Phase 3's file):
```ts
describe("complete method (extended for the Italian tutor phase)", () => {
  it("mock provider's complete returns a deterministic string", async () => {
    const p = llmMockProvider();
    const a = await p.complete("anything");
    const b = await p.complete("anything");
    expect(a).toBe(b);
  });

  it("http provider's complete throws without a configured key", async () => {
    const oldKey = process.env.LLM_HTTP_KEY;
    delete process.env.LLM_HTTP_KEY;
    await expect(llmHttpProvider().complete("x")).rejects.toThrow(/LLM_HTTP_KEY/);
    if (oldKey !== undefined) process.env.LLM_HTTP_KEY = oldKey;
  });
});
```

- [ ] **Step 4: Run the llm tests to verify the new tests fail**

Run:
```bash
npm test --workspace server -- providers/llm.test.ts
```
Expected: FAIL — `p.complete is not a function` (the interface and both providers lack `complete` yet); Phase 3's existing tests still pass.

- [ ] **Step 5: Extend the canonical LLM provider**

In `server/src/providers/llm.ts`:
- Add `complete(prompt: string): Promise<string>;` to the `LLMProvider` interface (keep `generateFlashcards`/`generateQuiz`).
- Add a `complete` implementation to the object returned by `llmHttpProvider()` that delegates to `httpComplete`:
```ts
    async complete(prompt: string): Promise<string> {
      return httpComplete(
        "You are a helpful assistant. Reply concisely using the exact format the user asks for.",
        prompt
      );
    }
```
- Add a `complete` implementation to the object returned by `mockProvider()` returning a deterministic, unparseable-as-content string:
```ts
    async complete(_prompt: string): Promise<string> {
      return '{"fallback":"canned"}';
    }
```
- Append the JSON-extraction helper used by the Italian module:
```ts
export function extractJson(text: string): Record<string, unknown> | null {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) return null;
  try {
    const parsed: unknown = JSON.parse(text.slice(start, end + 1));
    return typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
```

- [ ] **Step 6: Run the llm tests to verify they pass**

Run:
```bash
npm test --workspace server -- providers/llm.test.ts
```
Expected: all tests PASS (Phase 3 + the two new `complete` tests).

- [ ] **Step 7: Write the failing Italian router test**

`server/src/italian.test.ts`:
```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { createItalianRouter } from "./italian";
import { llmMockProvider, type LLMProvider } from "./providers/llm";

function mockComplete(output: string): LLMProvider {
  return { ...llmMockProvider(), complete: async () => output };
}

let token = "";
let ownerId = "";

beforeEach(async () => {
  await prisma.practiceLog.deleteMany({});
  await prisma.lesson.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "italian@example.com", password: "password123" });
  token = reg.body.token as string;
  ownerId = (reg.body.user as { id: string }).id;
});

test("lesson/today returns the canned lesson and persists it", async () => {
  const router = createItalianRouter(llmMockProvider());
  const res = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.date).toBe("2026-08-30");
  expect(res.body.title).toBe("Daily Italian: greetings");
  expect(res.body.vocab.length).toBeGreaterThan(0);
  expect(res.body.phrases.length).toBeGreaterThan(0);
  const id = res.body.id as string;

  const again = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(again.status).toBe(200);
  expect(again.body.id).toBe(id);
});

test("lesson/today with force=1 regenerates", async () => {
  const router = createItalianRouter(llmMockProvider());
  const first = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const second = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);
  expect(first.body.id).not.toBe(second.body.id);
});

test("rejects unauthenticated lesson access", async () => {
  const res = await request(app)
    .get("/api/italian/lesson/today")
    .query({ date: "2026-08-30" });
  expect(res.status).toBe(401);
});

test("mock provider with valid JSON is parsed and stored", async () => {
  const lessonJson = JSON.stringify({
    title: "Al bar",
    tip: "Caffè is coffee.",
    vocab: [
      { english: "coffee", italian: "caffè" },
      { english: "water", italian: "acqua" }
    ],
    phrases: [
      { english: "A coffee, please", italian: "Un caffè, per favore." },
      { english: "The bill, please", italian: "Il conto, per favore." }
    ]
  });
  const router = createItalianRouter(mockComplete(lessonJson));
  const res = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.title).toBe("Al bar");
  expect(res.body.phrases).toHaveLength(2);
});

test("mock provider with unparseable output falls back to the canned lesson", async () => {
  const router = createItalianRouter(mockComplete("this is not json at all"));
  const res = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.title).toBe("Daily Italian: greetings");
  expect(res.body.vocab.length).toBeGreaterThan(0);
});

test("tryParseLesson parses a fenced JSON block", async () => {
  const { tryParseLesson } = await import("./italian");
  const body = tryParseLesson(
    "```json\n{\"title\":\"x\",\"tip\":\"y\",\"vocab\":[{\"english\":\"hi\",\"italian\":\"ciao\"}],\"phrases\":[{\"english\":\"hi\",\"italian\":\"ciao\"}]}\n```"
  );
  expect(body?.title).toBe("x");
  expect(body?.phrases[0].italian).toBe("ciao");
});
```

- [ ] **Step 8: Run the test to verify it fails**

Run:
```bash
npm test --workspace server -- italian.test.ts
```
Expected: FAIL — `Cannot find module './italian'`.

- [ ] **Step 9: Implement the Italian lesson module + router**

`server/src/italian.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { getLLMProvider, extractJson, type LLMProvider } from "./providers/llm";
import type { ItalianLesson, ItalianVocabItem, ItalianPhrase } from "shared";

export function dateKeyFor(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export interface LessonBody {
  title: string;
  tip: string;
  vocab: ItalianVocabItem[];
  phrases: ItalianPhrase[];
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function dateParam(value: unknown): string | null {
  return typeof value === "string" && DATE_RE.test(value) ? value : null;
}

export function fallbackLessonBody(): LessonBody {
  return {
    title: "Daily Italian: greetings",
    tip: "Ciao means both hello and goodbye. Roll your r's — practice 'arrivederci' slowly.",
    vocab: [
      { english: "hello / goodbye", italian: "ciao" },
      { english: "goodbye", italian: "arrivederci" },
      { english: "please", italian: "per favore" },
      { english: "thank you", italian: "grazie" }
    ],
    phrases: [
      { english: "How are you?", italian: "Come stai?" },
      { english: "My name is…", italian: "Mi chiamo…" },
      { english: "I would like a coffee, please.", italian: "Vorrei un caffè, per favore." }
    ]
  };
}

export function lessonPrompt(dateKey: string): string {
  return [
    "You are a friendly Italian tutor. Create today's Italian lesson for a beginner.",
    `Today's date key: ${dateKey}`,
    "Return ONLY a JSON object with this exact shape:",
    '{"title": string, "tip": string, "vocab": [{"english": string, "italian": string}], "phrases": [{"english": string, "italian": string}]}',
    "Use 4 vocab items and 3 example phrases in simple everyday Italian."
  ].join("\n");
}

function isVocabItem(v: unknown): v is ItalianVocabItem {
  return (
    typeof v === "object" &&
    v !== null &&
    typeof (v as ItalianVocabItem).english === "string" &&
    typeof (v as ItalianVocabItem).italian === "string"
  );
}

export function tryParseLesson(text: string): LessonBody | null {
  const json = extractJson(text);
  if (!json) return null;
  if (typeof json.title !== "string" || typeof json.tip !== "string") return null;
  if (!Array.isArray(json.vocab) || !Array.isArray(json.phrases)) return null;
  const vocab = json.vocab.filter(isVocabItem);
  const phrases = json.phrases.filter(isVocabItem);
  if (vocab.length === 0 || phrases.length === 0) return null;
  return { title: json.title, tip: json.tip, vocab, phrases };
}

export async function generateLessonBody(provider: LLMProvider, dateKey: string): Promise<LessonBody> {
  try {
    const raw = await provider.complete(lessonPrompt(dateKey));
    return tryParseLesson(raw) ?? fallbackLessonBody();
  } catch {
    return fallbackLessonBody();
  }
}

function lessonBodyToStore(body: LessonBody): { title: string; tip: string; vocab: string; phrases: string } {
  return {
    title: body.title,
    tip: body.tip,
    vocab: JSON.stringify(body.vocab),
    phrases: JSON.stringify(body.phrases)
  };
}

function lessonFromRow(row: {
  id: string;
  date: string;
  title: string;
  tip: string;
  vocab: string;
  phrases: string;
}): ItalianLesson {
  return {
    id: row.id,
    date: row.date,
    title: row.title,
    tip: row.tip,
    vocab: JSON.parse(row.vocab) as ItalianVocabItem[],
    phrases: JSON.parse(row.phrases) as ItalianPhrase[]
  };
}

async function generateAndStore(provider: LLMProvider, ownerId: string, dateKey: string) {
  const body = await generateLessonBody(provider, dateKey);
  await prisma.lesson.deleteMany({ where: { ownerId, date: dateKey } });
  return prisma.lesson.create({
    data: { ownerId, date: dateKey, ...lessonBodyToStore(body) }
  });
}

export function createItalianRouter(provider: LLMProvider = getLLMProvider()): Router {
  const router = Router();
  router.use(requireAuth);

  router.get("/lesson/today", async (req: AuthedRequest, res) => {
    const dateKey = dateParam(req.query.date) ?? dateKeyFor(new Date());
    const force = req.query.force === "1";
    const existing = force ? null : await prisma.lesson.findFirst({ where: { ownerId: req.userId, date: dateKey } });
    const row = existing ?? (await generateAndStore(provider, req.userId!, dateKey));
    res.json(lessonFromRow(row));
  });

  return router;
}

export const italianRouter = createItalianRouter();
```

- [ ] **Step 10: Mount the router**

In `server/src/index.ts`, add the import and mount:
```ts
import { italianRouter } from "./italian";
```
```ts
app.use("/api/italian", italianRouter);
```

- [ ] **Step 11: Run server tests to verify they pass**

```bash
npm test --workspace server
```
Expected: all tests PASS (llm, italian, study, and prior suites).

- [ ] **Step 12: Typecheck and lint**

```bash
npm run typecheck
npm run lint --workspace server
```
Expected: no errors.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat(server): italian lesson of the day via extended llm provider"
```

---

### Task 4.2: Correction engine + feedback/translation endpoints

**Files:**
- Modify: `shared/src/index.ts` (append `MistakeType`, `ItalianCorrection`)
- Create: `server/src/correction.ts`
- Test: `server/src/correction.test.ts`
- Modify: `server/src/italian.ts` (add `POST /check` and `POST /translate` handlers)
- Modify: `server/src/italian.test.ts` (add check/translate/completion tests)

**Interfaces:**
- Consumes: `LLMProvider`, `extractJson` from `server/src/providers/llm.ts`; `ItalianPhrase`, `ItalianCorrection` from `shared`; `prisma`, `requireAuth`.
- Produces:
  - `normalizeForCompare(text: string): string` (pure; lowercase, punctuation-flattened comparison key)
  - `correctionPrompt(phraseEnglish, targetItalian, userAttempt): string`
  - `tryParseCorrection(phrase: ItalianPhrase, userAttempt: string, text: string): ItalianCorrection | null`
  - `fallbackCorrection(phrase: ItalianPhrase, userAttempt: string): ItalianCorrection`
  - `checkAttempt(provider: LLMProvider, phrase: ItalianPhrase, userAttempt: string): Promise<ItalianCorrection>` (falls back to `fallbackCorrection` on throw or unparseable output)
  - `fallbackTranslate(phrases: ItalianPhrase[], english: string): string`
  - `translatePrompt(english: string): string`
  - `translateToItalian(provider: LLMProvider, phrases: ItalianPhrase[], english: string): Promise<string>` (falls back to `fallbackTranslate` on throw or unparseable output)
  - New routes on the same router: `POST /check`, `POST /translate`.
  - Each `POST /check` records a `PracticeLog` row and — once every phrase of the day's lesson has a correct attempt — sets `Lesson.completedAt`.

- [ ] **Step 1: Append correction types to shared**

In `shared/src/index.ts` append:
```ts
export type MistakeType =
  | "correct"
  | "minor"
  | "vocabulary"
  | "grammar"
  | "word-order"
  | "incomplete"
  | "other";

export interface ItalianCorrection {
  isCorrect: boolean;
  phraseEnglish: string;
  targetItalian: string;
  userAttempt: string;
  mistakeType: MistakeType;
  hint: string;
  correctPhrase: string;
}
```

- [ ] **Step 2: Write the failing correction engine test**

`server/src/correction.test.ts`:
```ts
import { test, expect } from "vitest";
import {
  normalizeForCompare,
  tryParseCorrection,
  fallbackCorrection,
  checkAttempt,
  translateToItalian,
  fallbackTranslate
} from "./correction";
import { llmMockProvider, type LLMProvider } from "./providers/llm";
import type { ItalianPhrase } from "shared";

const phrase: ItalianPhrase = { english: "How are you?", italian: "Come stai?" };

function mockComplete(output: string): LLMProvider {
  return { ...llmMockProvider(), complete: async () => output };
}

test("normalizeForCompare flattens punctuation and case", () => {
  expect(normalizeForCompare("  Come        Stai?  ")).toBe("come stai");
  expect(normalizeForCompare("C'è un caffè")).toBe("c'è un caffè");
});

test("tryParseCorrection parses a valid LLM correction response", () => {
  const text = JSON.stringify({
    isCorrect: false,
    mistakeType: "grammar",
    hint: "Verb goes second.",
    correctPhrase: "Come stai?"
  });
  const result = tryParseCorrection(phrase, "come stai", text);
  expect(result).not.toBeNull();
  expect(result?.isCorrect).toBe(false);
  expect(result?.mistakeType).toBe("grammar");
  expect(result?.hint).toBe("Verb goes second.");
  expect(result?.correctPhrase).toBe("Come stai?");
  expect(result?.phraseEnglish).toBe("How are you?");
  expect(result?.targetItalian).toBe("Come stai?");
});

test("tryParseCorrection returns null for non-JSON LLM output", () => {
  expect(tryParseCorrection(phrase, "come stai", "I think you meant…")).toBeNull();
});

test("tryParseCorrection returns null for an invalid mistakeType", () => {
  const text = JSON.stringify({ isCorrect: false, mistakeType: "typo", hint: "h", correctPhrase: "Come stai?" });
  expect(tryParseCorrection(phrase, "come stai", text)).toBeNull();
});

test("fallbackCorrection marks a normalized-equal attempt correct", () => {
  const result = fallbackCorrection(phrase, " come  stai? ");
  expect(result.isCorrect).toBe(true);
  expect(result.mistakeType).toBe("correct");
});

test("fallbackCorrection marks a wrong attempt incorrect with a hint", () => {
  const result = fallbackCorrection(phrase, "non lo so");
  expect(result.isCorrect).toBe(false);
  expect(result.hint.length).toBeGreaterThan(0);
  expect(result.correctPhrase).toBe("Come stai?");
});

test("checkAttempt uses the deterministic fallback when the provider output is unparseable", async () => {
  const correct = await checkAttempt(llmMockProvider(), phrase, "Come stai?");
  expect(correct.isCorrect).toBe(true);
  const wrong = await checkAttempt(llmMockProvider(), phrase, "Buongiorno");
  expect(wrong.isCorrect).toBe(false);
});

test("checkAttempt parses a mock provider's correction JSON", async () => {
  const provider = mockComplete(
    JSON.stringify({ isCorrect: true, mistakeType: "correct", hint: "", correctPhrase: "Come stai?" })
  );
  const result = await checkAttempt(provider, phrase, "Come stai?");
  expect(result.isCorrect).toBe(true);
  expect(result.mistakeType).toBe("correct");
});

test("translateToItalian parses a mock provider translation", async () => {
  const provider = mockComplete(JSON.stringify({ italian: "Come stai?" }));
  const result = await translateToItalian(provider, [phrase], "How are you?");
  expect(result).toBe("Come stai?");
});

test("fallbackTranslate maps a known phrase via normalized English", () => {
  expect(fallbackTranslate([phrase], "how are you?")).toBe("Come stai?");
});

test("fallbackTranslate returns a canned fallback for unknown input", () => {
  expect(fallbackTranslate([phrase], "I need the train station")).toBe("Non ho capito, puoi ripetere?");
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run:
```bash
npm test --workspace server -- correction.test.ts
```
Expected: FAIL — `Cannot find module './correction'`.

- [ ] **Step 4: Implement the correction engine**

`server/src/correction.ts`:
```ts
import { extractJson, type LLMProvider } from "./providers/llm";
import type { ItalianCorrection, ItalianPhrase, MistakeType } from "shared";

export const MISTAKE_TYPES: readonly MistakeType[] = [
  "correct", "minor", "vocabulary", "grammar", "word-order", "incomplete", "other"
];

export function normalizeForCompare(text: string): string {
  return text
    .toLowerCase()
    .replace(/[\u2018\u2019`']/g, "'")
    .replace(/[-.,!?;:«»…—–()]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function correctionPrompt(phraseEnglish: string, targetItalian: string, userAttempt: string): string {
  return [
    "You are an Italian tutor. The learner tried to say a phrase in Italian.",
    `English prompt: ${phraseEnglish}`,
    `Learner said: ${userAttempt}`,
    `Correct Italian phrase: ${targetItalian}`,
    'Reply with ONLY JSON: {"isCorrect": boolean, "mistakeType": "correct"|"minor"|"vocabulary"|"grammar"|"word-order"|"incomplete"|"other", "hint": string, "correctPhrase": string}',
    "If the learner is close, mark isCorrect true and keep hint empty. Be friendly."
  ].join("\n");
}

export function fallbackCorrection(phrase: ItalianPhrase, userAttempt: string): ItalianCorrection {
  const isCorrect =
    userAttempt.trim() !== "" && normalizeForCompare(userAttempt) === normalizeForCompare(phrase.italian);
  return {
    isCorrect,
    phraseEnglish: phrase.english,
    targetItalian: phrase.italian,
    userAttempt,
    mistakeType: isCorrect ? "correct" : "vocabulary",
    hint: isCorrect ? "Perfetto!" : `Not quite. Try again — it starts with "${phrase.italian.split(" ")[0]}".`,
    correctPhrase: phrase.italian
  };
}

export function tryParseCorrection(
  phrase: ItalianPhrase,
  userAttempt: string,
  text: string
): ItalianCorrection | null {
  const json = extractJson(text);
  if (!json) return null;
  if (typeof json.isCorrect !== "boolean") return null;
  if (typeof json.mistakeType !== "string" || !(MISTAKE_TYPES as readonly string[]).includes(json.mistakeType)) {
    return null;
  }
  if (typeof json.hint !== "string" || typeof json.correctPhrase !== "string") return null;
  return {
    isCorrect: json.isCorrect,
    phraseEnglish: phrase.english,
    targetItalian: phrase.italian,
    userAttempt,
    mistakeType: json.mistakeType as MistakeType,
    hint: json.hint,
    correctPhrase: json.correctPhrase || phrase.italian
  };
}

export async function checkAttempt(
  provider: LLMProvider,
  phrase: ItalianPhrase,
  userAttempt: string
): Promise<ItalianCorrection> {
  try {
    const raw = await provider.complete(correctionPrompt(phrase.english, phrase.italian, userAttempt));
    return tryParseCorrection(phrase, userAttempt, raw) ?? fallbackCorrection(phrase, userAttempt);
  } catch {
    return fallbackCorrection(phrase, userAttempt);
  }
}

export function translatePrompt(english: string): string {
  return [
    "You are an Italian tutor. Translate the learner's English into natural Italian.",
    `English: ${english}`,
    'Reply with ONLY JSON: {"italian": string}'
  ].join("\n");
}

export function fallbackTranslate(phrases: ItalianPhrase[], english: string): string {
  const norm = normalizeForCompare(english);
  for (const p of phrases) {
    if (normalizeForCompare(p.english) === norm) return p.italian;
  }
  return "Non ho capito, puoi ripetere?";
}

export async function translateToItalian(
  provider: LLMProvider,
  phrases: ItalianPhrase[],
  english: string
): Promise<string> {
  try {
    const raw = await provider.complete(translatePrompt(english));
    const json = extractJson(raw);
    if (json && typeof json.italian === "string" && json.italian.trim()) return json.italian.trim();
  } catch {
    /* fall through to fallbackTranslate */
  }
  return fallbackTranslate(phrases, english);
}
```

- [ ] **Step 5: Run the correction test to verify it passes**

```bash
npm test --workspace server -- correction.test.ts
```
Expected: all correction tests PASS.

- [ ] **Step 6: Add the check and translate handlers to the router**

In `server/src/italian.ts` change the import line:
```ts
import { checkAttempt, translateToItalian } from "./correction";
```
Add above `createItalianRouter` the completion helper:
```ts
async function maybeCompleteLesson(
  ownerId: string,
  lessonId: string,
  dateKey: string,
  phrases: ItalianPhrase[]
): Promise<void> {
  const rows = await prisma.practiceLog.findMany({
    where: { ownerId, lessonDate: dateKey, isCorrect: true },
    select: { phraseIndex: true }
  });
  const correctIndices = new Set(rows.map((r) => r.phraseIndex));
  if (correctIndices.size < phrases.length) return;
  await prisma.lesson.update({ where: { id: lessonId }, data: { completedAt: new Date() } });
}
```
Inside `createItalianRouter`, before `return router;` add:
```ts
  router.post("/check", async (req: AuthedRequest, res) => {
    const { phraseIndex, userAttempt, dateKey } = req.body ?? {};
    if (typeof phraseIndex !== "number" || !Number.isInteger(phraseIndex) || phraseIndex < 0) {
      return res.status(400).json({ error: "phraseIndex required" });
    }
    if (typeof userAttempt !== "string" || !userAttempt.trim()) {
      return res.status(400).json({ error: "userAttempt required" });
    }
    const attempt = userAttempt.trim();
    const date = dateParam(dateKey) ?? dateKeyFor(new Date());
    const lessonRow = await prisma.lesson.findFirst({ where: { ownerId: req.userId, date } });
    if (!lessonRow) return res.status(404).json({ error: `no lesson for ${date}` });
    const phrases = JSON.parse(lessonRow.phrases) as ItalianPhrase[];
    const phrase = phrases[phraseIndex];
    if (!phrase) return res.status(404).json({ error: "phrase index out of range" });

    const correction = await checkAttempt(provider, phrase, attempt);
    await prisma.practiceLog.create({
      data: {
        ownerId: req.userId!,
        lessonDate: date,
        phraseIndex,
        isCorrect: correction.isCorrect,
        userAttempt: attempt
      }
    });
    if (correction.isCorrect) await maybeCompleteLesson(req.userId!, lessonRow.id, date, phrases);
    res.json(correction);
  });

  router.post("/translate", async (req: AuthedRequest, res) => {
    const { text, dateKey } = req.body ?? {};
    if (typeof text !== "string" || !text.trim()) {
      return res.status(400).json({ error: "text required" });
    }
    const date = dateParam(dateKey) ?? dateKeyFor(new Date());
    const lessonRow = await prisma.lesson.findFirst({ where: { ownerId: req.userId, date } });
    const phrases = lessonRow ? (JSON.parse(lessonRow.phrases) as ItalianPhrase[]) : [];
    const italian = await translateToItalian(provider, phrases, text.trim());
    res.json({ italian });
  });
```

- [ ] **Step 7: Write the failing router tests for check and translate**

Append to `server/src/italian.test.ts` (reuses `token`, `ownerId`, `mockComplete`, and `llmMockProvider` imports from Task 4.1 Step 7):
```ts
test("check: mock provider correction is parsed into structured feedback", async () => {
  const correctionJson = JSON.stringify({
    isCorrect: true,
    mistakeType: "correct",
    hint: "",
    correctPhrase: "Come stai?"
  });
  const router = createItalianRouter(mockComplete(correctionJson));
  await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);

  const res = await request(router)
    .post("/check")
    .send({ dateKey: "2026-08-30", phraseIndex: 0, userAttempt: "Come stai?" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body).toMatchObject({
    isCorrect: true,
    mistakeType: "correct",
    phraseEnglish: "How are you?",
    targetItalian: "Come stai?",
    correctPhrase: "Come stai?"
  });
  const logs = await prisma.practiceLog.findMany({ where: { ownerId } });
  expect(logs).toHaveLength(1);
  expect(logs[0].isCorrect).toBe(true);
});

test("check: out-of-range phrase index returns 404", async () => {
  const router = createItalianRouter(llmMockProvider());
  await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const res = await request(router)
    .post("/check")
    .send({ dateKey: "2026-08-30", phraseIndex: 99, userAttempt: "ciao" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(404);
});

test("check: one correct phrase does not complete the lesson", async () => {
  const router = createItalianRouter(llmMockProvider());
  const lesson = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const first = lesson.body.phrases[0] as { italian: string };
  await request(router)
    .post("/check")
    .send({ dateKey: "2026-08-30", phraseIndex: 0, userAttempt: first.italian })
    .set("Authorization", `Bearer ${token}`);
  const stored = await prisma.lesson.findFirst({ where: { ownerId, date: "2026-08-30" } });
  expect(stored?.completedAt).toBeFalsy();
});

test("check: correct recital of every phrase completes the lesson", async () => {
  const router = createItalianRouter(llmMockProvider());
  const lesson = await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const phrases = lesson.body.phrases as Array<{ italian: string }>;
  for (let i = 0; i < phrases.length; i++) {
    const res = await request(router)
      .post("/check")
      .send({ dateKey: "2026-08-30", phraseIndex: i, userAttempt: phrases[i].italian })
      .set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.isCorrect).toBe(true);
  }
  const stored = await prisma.lesson.findFirst({ where: { ownerId, date: "2026-08-30" } });
  expect(stored?.completedAt).toBeTruthy();
});

test("translate: known phrase maps to its Italian, unknown falls back", async () => {
  const router = createItalianRouter(llmMockProvider());
  await request(router)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);

  const known = await request(router)
    .post("/translate")
    .send({ dateKey: "2026-08-30", text: "How are you?" })
    .set("Authorization", `Bearer ${token}`);
  expect(known.status).toBe(200);
  expect(known.body.italian).toBe("Come stai?");

  const unknown = await request(router)
    .post("/translate")
    .send({ dateKey: "2026-08-30", text: "I need the train station" })
    .set("Authorization", `Bearer ${token}`);
  expect(unknown.body.italian).toBe("Non ho capito, puoi ripetere?");
});
```

- [ ] **Step 8: Run the router tests to verify they fail**

Run:
```bash
npm test --workspace server -- italian.test.ts
```
Expected: FAIL for the new tests — `POST /check` and `POST /translate` return 404 (routes not added yet).

- [ ] **Step 9: Run the router tests to verify they pass after the Step 6 implementation**

```bash
npm test --workspace server
```
Expected: all tests PASS, including the check/translate/completion suite.

- [ ] **Step 10: Typecheck and lint**

```bash
npm run typecheck
npm run lint --workspace server
```
Expected: no errors.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(server): italian feedback and translation endpoints with practice logging"
```

---

### Task 4.3: Client speech utilities (STT + TTS)

**Files:**
- Modify: `client/package.json` (add vitest + `test` script)
- Create: `client/src/speech.ts`
- Test: `client/src/speech.test.ts`

**Interfaces:**
- Consumes: nothing from earlier tasks (pure browser-adjacent module).
- Produces:
  - `interface SpeechResult { transcript: string; isFinal: boolean }`
  - `isSpeechRecognitionSupported(): boolean`
  - `createRecognizer(lang: string): SpeechRecognitionLike | null`
  - `startListening(opts: { lang?: string; onResult?: (r: SpeechResult) => void; onEnd?: () => void; onError?: (err: string) => void }): () => void`
  - `speak(text: string, lang?: string): void`
  - `preferVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null` (prefers `it-IT` then any Italian)
  - `normalizeTranscript(transcript: string): string`
  - `warmVoices(): Promise<void>`

  Browser APIs (`SpeechRecognition`, `speechSynthesis`) cannot run under vitest's node environment, so the unit tests cover the pure helpers `preferVoice` and `normalizeTranscript`; STT/TTS paths get a manual verification step.

- [ ] **Step 1: Add vitest and a test script to the client**

In `client/package.json` add to `devDependencies`:
```json
"vitest": "^2.0.0"
```
and to `scripts`:
```json
"test": "vitest run"
```
Run `npm install`.

- [ ] **Step 2: Write the failing speech test (pure helpers)**

`client/src/speech.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { preferVoice, normalizeTranscript } from "./speech";

function fakeVoice(lang: string, name: string): SpeechSynthesisVoice {
  return { lang, name, localService: true } as unknown as SpeechSynthesisVoice;
}

describe("preferVoice", () => {
  it("prefers an it-IT voice", () => {
    const voices = [fakeVoice("en-US", "US English"), fakeVoice("it-IT", "Alice Italian"), fakeVoice("it-CH", "Swiss Italian")];
    expect(preferVoice(voices)?.lang).toBe("it-IT");
  });

  it("falls back to any Italian voice", () => {
    const voices = [fakeVoice("en-US", "US English"), fakeVoice("it-CH", "Swiss Italian")];
    expect(preferVoice(voices)?.lang).toBe("it-CH");
  });

  it("returns null when no Italian voice exists", () => {
    expect(preferVoice([fakeVoice("en-US", "US English")])).toBeNull();
  });
});

describe("normalizeTranscript", () => {
  it("collapses whitespace and trims", () => {
    expect(normalizeTranscript("  Come   stai? ")).toBe("Come stai?");
  });

  it("keeps punctuation and accents", () => {
    expect(normalizeTranscript("C'è un caffè?")).toBe("C'è un caffè?");
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npm test --workspace client
```
Expected: FAIL — `Cannot find module './speech'`.

- [ ] **Step 4: Implement the speech utilities**

`client/src/speech.ts`:
```ts
export interface SpeechResult {
  transcript: string;
  isFinal: boolean;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  start(): void;
  stop(): void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: { error: string }) => void) | null;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: {
    readonly length: number;
    [index: number]: {
      isFinal: boolean;
      readonly length: number;
      [index: number]: { transcript: string };
    };
  };
}

declare global {
  interface Window {
    SpeechRecognition?: { new (): SpeechRecognitionLike };
    webkitSpeechRecognition?: { new (): SpeechRecognitionLike };
  }
}

export function isSpeechRecognitionSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    (("SpeechRecognition" in window) as boolean || ("webkitSpeechRecognition" in window) as boolean)
  );
}

export function createRecognizer(lang: string): SpeechRecognitionLike | null {
  if (!isSpeechRecognitionSupported()) return null;
  const Ctor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!Ctor) return null;
  const rec = new Ctor();
  rec.lang = lang;
  rec.interimResults = true;
  rec.continuous = false;
  return rec;
}

export function startListening(opts: {
  lang?: string;
  onResult?: (result: SpeechResult) => void;
  onEnd?: () => void;
  onError?: (message: string) => void;
}): () => void {
  const rec = createRecognizer(opts.lang ?? "en-US");
  if (!rec) {
    opts.onError?.("speech recognition is not supported in this browser");
    return () => {};
  }
  let finalTranscript = "";
  rec.onresult = (event) => {
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i];
      if (result.isFinal) finalTranscript += result[0].transcript;
    }
    const interim = Array.from({ length: event.results.length }, (_, i) => event.results[i])
      .filter((r) => !r.isFinal)
      .map((r) => r[0].transcript)
      .join("");
    opts.onResult?.({
      transcript: finalTranscript + interim,
      isFinal: finalTranscript.length > 0 && interim === ""
    });
  };
  rec.onerror = (event) => opts.onError?.(event.error);
  rec.onend = () => opts.onEnd?.();
  rec.start();
  return () => {
    try {
      rec.stop();
    } catch {
      /* already stopped */
    }
  };
}

function isItalian(lang: string): boolean {
  return lang.toLowerCase().replace("_", "-").startsWith("it");
}

export function preferVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  const itIT = voices.find((v) => v.lang.toLowerCase().replace("_", "-").startsWith("it-it"));
  if (itIT) return itIT;
  const anyIt = voices.find((v) => isItalian(v.lang));
  return anyIt ?? null;
}

export function speak(text: string, lang = "it-IT"): void {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang;
  const voice = preferVoice(window.speechSynthesis.getVoices());
  if (voice) utterance.voice = voice;
  window.speechSynthesis.speak(utterance);
}

export function warmVoices(): Promise<void> {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return Promise.resolve();
  return new Promise((resolve) => {
    if (window.speechSynthesis.getVoices().length > 0) {
      resolve();
      return;
    }
    const onChange = () => {
      resolve();
      window.speechSynthesis.removeEventListener("voiceschanged", onChange);
    };
    window.speechSynthesis.addEventListener("voiceschanged", onChange);
  });
}

export function normalizeTranscript(transcript: string): string {
  return transcript.replace(/\s+/g, " ").trim();
}
```

- [ ] **Step 5: Run the client test to verify it passes**

```bash
npm test --workspace client
```
Expected: 5 tests PASS.

- [ ] **Step 6: Typecheck, lint**

```bash
npm run typecheck
npm run lint --workspace client
```
Expected: no errors.

- [ ] **Step 7: Manual browser verification (required — browser APIs untestable)**

Start both dev servers (`npm run dev --workspace server`, then `npm run dev --workspace client`), open `http://localhost:5173` in Chrome/Edge (desktop) and in Safari (if handy):
1. In DevTools console confirm `preferVoice(speechSynthesis.getVoices())` selects an Italian voice (or `null` if Chrome lacks one — acceptable; `speak` still sets `utterance.lang = "it-IT"`).
2. Confirm `speechSynthesis` speaks a short Italian string when `speak("Ciao, come stai?")` is called — audio plays through the speakers.
3. Grant microphone permission; confirm `startListening({ lang: "it-IT" })` returns a transcript for a spoken Italian phrase. This verifies the Web Speech API wiring; note quality is "decent, not Siri-grade" per the spec.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(client): browser speech stt and tts utilities"
```

---

### Task 4.4: Lesson + practice UI

**Files:**
- Create: `client/src/italianApi.ts`
- Create: `client/src/ItalianLesson.tsx`
- Modify: `client/src/App.tsx` (render the view once authed)

**Interfaces:**
- Consumes: `authedFetch` from `client/src/auth.ts`; speech helpers from Task 4.3 (`startListening`, `speak`, `warmVoices`, `normalizeTranscript`, `isSpeechRecognitionSupported`); shared types `ItalianLesson`, `ItalianCorrection`; server routes `GET /api/italian/lesson/today` (Task 4.1), `POST /api/italian/check` and `POST /api/italian/translate` (Task 4.2).
- Produces:
  - `client/src/italianApi.ts`: `getTodaysLesson(token, options?: { force?: boolean }): Promise<ItalianLesson>`, `checkAttempt(token, input: { dateKey: string; phraseIndex: number; userAttempt: string }): Promise<ItalianCorrection>`, `translateText(token, text: string): Promise<string>`
  - `client/src/ItalianLesson.tsx`: default-exports `ItalianLesson({ token, streak?, totalLessons? })` rendering vocab cards, phrase list with selectable active phrase, a recite flow (STT in `it-IT` → `checkAttempt` → structured feedback → retry), and a "Speak English → Italian" tutor panel (STT in `en-US` → `translateText` → TTS). The progress chip renders only when `streak` and `totalLessons` props are provided (wired in Task 4.5).

- [ ] **Step 1: Write the Italian API client**

`client/src/italianApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { ItalianLesson, ItalianCorrection } from "shared";

interface TranslateResult {
  italian: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getTodaysLesson(
  token: string,
  options?: { force?: boolean }
): Promise<ItalianLesson> {
  const qs = options?.force ? "?force=1" : "";
  const res = await authedFetch(token, `/api/italian/lesson/today${qs}`);
  return json<ItalianLesson>(res);
}

export async function checkAttempt(
  token: string,
  input: { dateKey: string; phraseIndex: number; userAttempt: string }
): Promise<ItalianCorrection> {
  const res = await authedFetch(token, "/api/italian/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<ItalianCorrection>(res);
}

export async function translateText(token: string, text: string): Promise<string> {
  const res = await authedFetch(token, "/api/italian/translate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text })
  });
  const result = await json<TranslateResult>(res);
  return result.italian;
}
```

- [ ] **Step 2: Write the ItalianLesson view**

`client/src/ItalianLesson.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import type { ItalianCorrection, ItalianLesson as Lesson } from "shared";
import { getTodaysLesson, checkAttempt, translateText } from "./italianApi";
import {
  startListening,
  speak,
  warmVoices,
  normalizeTranscript,
  isSpeechRecognitionSupported
} from "./speech";

type PracticeState =
  | { status: "idle" }
  | { status: "listening"; interim: string }
  | { status: "submitting" }
  | { status: "done"; correction: ItalianCorrection };

interface Props {
  token: string;
  streak?: number;
  totalLessons?: number;
}

export default function ItalianLesson({ token, streak, totalLessons }: Props) {
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [practice, setPractice] = useState<PracticeState>({ status: "idle" });
  const [attemptText, setAttemptText] = useState("");
  const [tutorInput, setTutorInput] = useState("");
  const [tutorItalian, setTutorItalian] = useState<string | null>(null);
  const [tutorListening, setTutorListening] = useState(false);

  const speechSupported = isSpeechRecognitionSupported();
  const phrase = lesson?.phrases[activeIndex];

  const loadLesson = useCallback(async () => {
    try {
      const data = await getTodaysLesson(token);
      setLesson(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to load the lesson");
    }
  }, [token]);

  useEffect(() => {
    loadLesson();
    warmVoices();
  }, [loadLesson]);

  async function submitAttempt(attempt: string) {
    if (!lesson || !phrase || !attempt) return;
    setPractice({ status: "submitting" });
    try {
      const correction = await checkAttempt(token, {
        dateKey: lesson.date,
        phraseIndex: activeIndex,
        userAttempt: attempt
      });
      setPractice({ status: "done", correction });
    } catch {
      setPractice({ status: "idle" });
      setError("Could not get feedback — is the server running?");
    }
  }

  function startRecite() {
    if (!phrase || practice.status !== "idle") return;
    let accumulated = "";
    setPractice({ status: "listening", interim: "" });
    startListening({
      lang: "it-IT",
      onResult: (r) => {
        accumulated = r.transcript;
        setPractice({ status: "listening", interim: r.transcript });
      },
      onEnd: () => {
        const attempt = normalizeTranscript(accumulated);
        if (attempt) submitAttempt(attempt);
        else setPractice({ status: "idle" });
      },
      onError: () => setPractice({ status: "idle" })
    });
  }

  function startTutor() {
    let accumulated = "";
    setTutorListening(true);
    setTutorItalian(null);
    startListening({
      lang: "en-US",
      onResult: (r) => {
        accumulated = r.transcript;
        setTutorInput(r.transcript);
      },
      onEnd: async () => {
        setTutorListening(false);
        const text = normalizeTranscript(accumulated);
        if (!text) return;
        try {
          const italian = await translateText(token, text);
          setTutorItalian(italian);
        } catch {
          setError("Translation failed — is the server running?");
        }
      },
      onError: () => setTutorListening(false)
    });
  }

  if (error) return <p className="text-red-600 text-sm">{error}</p>;
  if (!lesson) return <p className="text-slate-400 text-sm">Loading today's lesson…</p>;

  return (
    <section className="w-full max-w-2xl flex flex-col gap-5">
      <header className="flex items-baseline justify-between">
        <h2 className="text-xl font-semibold text-slate-800">{lesson.title}</h2>
        <span className="text-xs text-slate-500">{lesson.date}</span>
      </header>

      {streak !== undefined && totalLessons !== undefined && (
        <p className="text-xs text-slate-400">
          {streak} day streak · {totalLessons} lessons completed
        </p>
      )}

      <p className="text-sm text-slate-600 italic">{lesson.tip}</p>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Vocab</h3>
        <ul className="grid grid-cols-2 gap-2">
          {lesson.vocab.map((v) => (
            <li key={v.italian} className="border rounded p-2 bg-white text-sm">
              <span className="text-slate-500">{v.english}</span>
              <span className="block font-medium text-slate-800">{v.italian}</span>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Phrases</h3>
        <ul className="flex flex-col gap-2 text-sm">
          {lesson.phrases.map((p, i) => (
            <li
              key={p.italian}
              className={`border rounded p-3 flex items-center justify-between gap-3 cursor-pointer ${
                i === activeIndex ? "border-indigo-400 bg-indigo-50" : "bg-white"
              }`}
              onClick={() => {
                setActiveIndex(i);
                setPractice({ status: "idle" });
              }}
            >
              <span className="text-slate-600">{p.english}</span>
              <span className="font-medium text-slate-800">{p.italian}</span>
            </li>
          ))}
        </ul>
      </div>

      {phrase && (
        <div className="border rounded bg-white p-4 flex flex-col gap-3">
          <h3 className="font-semibold text-slate-700">Practice — speak Italian</h3>
          <p className="text-slate-600">
            Say: <span className="font-medium text-slate-800">{phrase.english}</span>
          </p>

          {practice.status === "listening" && (
            <p className="text-sm text-indigo-600">Listening… “{practice.interim}”</p>
          )}
          {practice.status === "submitting" && <p className="text-sm text-slate-400">Checking…</p>}
          {practice.status === "done" && (
            <div className="flex flex-col gap-2 text-sm">
              <p className={practice.correction.isCorrect ? "text-green-700" : "text-red-700"}>
                {practice.correction.isCorrect ? "Correct — bravo!" : "Almost — keep trying."}
              </p>
              {!practice.correction.isCorrect && (
                <p className="text-slate-600">Hint: {practice.correction.hint}</p>
              )}
              <p className="text-slate-600">
                Correct form:{" "}
                <span className="font-medium text-slate-800">{practice.correction.correctPhrase}</span>
                <button
                  className="ml-2 text-indigo-600 underline"
                  onClick={() => speak(practice.correction.correctPhrase)}
                >
                  Hear it
                </button>
              </p>
              <div className="flex gap-2">
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => setPractice({ status: "idle" })}
                >
                  Try again
                </button>
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => {
                    setActiveIndex((prev) => (prev + 1) % lesson.phrases.length);
                    setPractice({ status: "idle" });
                  }}
                >
                  Next phrase
                </button>
              </div>
            </div>
          )}

          {practice.status === "idle" && (
            <div className="flex flex-col gap-2">
              <div className="flex gap-2 items-center">
                <button
                  className="bg-indigo-600 text-white rounded px-4 py-1"
                  onClick={startRecite}
                  disabled={!speechSupported}
                >
                  {speechSupported ? "Speak Italian" : "Mic unavailable"}
                </button>
                <span className="text-xs text-slate-400">
                  {speechSupported ? "uses your microphone (it-IT)" : "microphone unsupported — type below"}
                </span>
              </div>
              <div className="flex gap-2">
                <input
                  className="border rounded px-3 py-1 flex-1"
                  placeholder="…or type the Italian here"
                  value={attemptText}
                  onChange={(e) => setAttemptText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      submitAttempt(normalizeTranscript(attemptText));
                      setAttemptText("");
                    }
                  }}
                />
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => {
                    submitAttempt(normalizeTranscript(attemptText));
                    setAttemptText("");
                  }}
                >
                  Submit
                </button>
              </div>
              <button
                className="self-start text-indigo-600 underline text-sm"
                onClick={() => speak(phrase.italian)}
              >
                Hear the correct Italian
              </button>
            </div>
          )}
        </div>
      )}

      <div className="border rounded bg-white p-4 flex flex-col gap-3">
        <h3 className="font-semibold text-slate-700">Tutor — speak English, learn Italian</h3>
        <div className="flex gap-2">
          <button
            className="bg-indigo-600 text-white rounded px-4 py-1"
            onClick={startTutor}
            disabled={!speechSupported || tutorListening}
          >
            {tutorListening ? "Listening…" : "Speak English"}
          </button>
          <input
            className="border rounded px-3 py-1 flex-1 text-sm"
            placeholder="…or type an English phrase"
            value={tutorInput}
            onChange={(e) => setTutorInput(e.target.value)}
            onKeyDown={async (e) => {
              if (e.key !== "Enter") return;
              const text = normalizeTranscript(tutorInput);
              if (!text) return;
              try {
                const italian = await translateText(token, text);
                setTutorItalian(italian);
              } catch {
                setError("Translation failed — is the server running?");
              }
            }}
          />
        </div>
        {tutorItalian && (
          <p className="text-sm text-slate-700">
            <span className="font-medium text-slate-800">{tutorItalian}</span>{" "}
            <button className="ml-2 text-indigo-600 underline" onClick={() => speak(tutorItalian)}>
              Hear it
            </button>
          </p>
        )}
      </div>
    </section>
  );
}
```

- [ ] **Step 3: Wire the view into App**

Modify `client/src/App.tsx`: add the import and render the view inside the logged-in branch, after existing logged-in content (place it after the `TaskList` if present from Phase 1, or after the greeting otherwise):
```tsx
import ItalianLesson from "./ItalianLesson";
```
```tsx
{token && user && (
  <div className="w-full flex flex-col items-center gap-6">
    <p className="text-center text-slate-600">
      Logged in as <span className="font-semibold">{user.email}</span>
    </p>
    <TaskList token={token} />
    <ItalianLesson token={token} />
    <button
      className="text-sm text-red-600 underline"
      onClick={() => {
        localStorage.removeItem("dtd.token");
        localStorage.removeItem("dtd.user");
        setToken(null);
        setUser(null);
      }}
    >
      Log out
    </button>
  </div>
)}
```

- [ ] **Step 4: Typecheck, lint**

```bash
npm run typecheck
npm run lint --workspace client
```
Expected: no errors.

- [ ] **Step 5: Manual browser verification (required — voice UI untestable in vitest)**

Start both dev servers, log in, navigate to the Italian lesson:
1. The lesson header, vocab cards, tip, and phrase list render; content matches "Daily Italian: greetings".
2. Click "Speak Italian" (or type) — a correct Italian attempt shows "Correct — bravo!"; a wrong one shows a hint and the correct form; "Try again" resets; "Hear it" speaks the correct phrase via `speechSynthesis`.
3. Click "Speak English" and speak an English sentence — an Italian translation appears with a "Hear it" button that speaks it.
4. Reload the page — the same lesson returns (cached by the server for the day).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(client): italian lesson and voice practice ui"
```

---

### Task 4.5: Progress tracking (streak)

**Files:**
- Modify: `shared/src/index.ts` (append `ItalianProgress`)
- Modify: `server/src/italian.ts` (add `GET /progress`)
- Modify: `server/src/italian.test.ts` (progress tests)
- Modify: `client/src/italianApi.ts` (add `getProgress`)
- Modify: `client/src/App.tsx` (fetch progress, pass streak/totalLessons to `ItalianLesson`)

**Interfaces:**
- Consumes: `PracticeLog` and `Lesson.completedAt` (recorded since Task 4.1 schema; populated since Task 4.2 `check` handler); `dateKeyFor` from `italian.ts`; `ItalianProgress` from `shared`.
- Produces: server route `GET /api/italian/progress?from=YYYY-MM-DD` → `ItalianProgress { streak, totalLessons, totalPractice }` (a day is "active" if it has a `PracticeLog` row or a `Lesson.completedAt`); client `getProgress(token): Promise<ItalianProgress>`; `App.tsx` passes real values into the `ItalianLesson` progress chip (rendered since Task 4.4).

- [ ] **Step 1: Append the progress type to shared**

In `shared/src/index.ts` append:
```ts
export interface ItalianProgress {
  streak: number;       // consecutive active days ending at the anchor date
  totalLessons: number; // lessons with completedAt set
  totalPractice: number; // practice attempts logged
}
```

- [ ] **Step 2: Write the failing progress tests**

Append to `server/src/italian.test.ts`:
```ts
test("progress: computes streak from practice logs", async () => {
  await prisma.practiceLog.create({
    data: { ownerId, lessonDate: "2026-08-30", phraseIndex: 0, isCorrect: true, userAttempt: "ciao", createdAt: new Date(2026, 7, 30, 9) }
  });
  await prisma.practiceLog.create({
    data: { ownerId, lessonDate: "2026-08-29", phraseIndex: 0, isCorrect: true, userAttempt: "ciao", createdAt: new Date(2026, 7, 29, 9) }
  });
  const res = await request(app)
    .get("/api/italian/progress")
    .query({ from: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ streak: 2, totalLessons: 0, totalPractice: 2 });
});

test("progress: counts completed lessons and resets streak on a gap", async () => {
  await prisma.lesson.create({
    data: {
      ownerId,
      date: "2026-08-30",
      title: "x",
      tip: "y",
      vocab: "[]",
      phrases: "[{\"english\":\"hi\",\"italian\":\"ciao\"}]",
      completedAt: new Date(2026, 7, 30, 8)
    }
  });
  await prisma.practiceLog.create({
    data: { ownerId, lessonDate: "2026-08-27", phraseIndex: 0, isCorrect: true, userAttempt: "ciao", createdAt: new Date(2026, 7, 27, 9) }
  });
  const res = await request(app)
    .get("/api/italian/progress")
    .query({ from: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.streak).toBe(1); // 30 active, 29/28 inactive, 27 active
  expect(res.body.totalLessons).toBe(1);
  expect(res.body.totalPractice).toBe(1);
});
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
npm test --workspace server -- italian.test.ts
```
Expected: FAIL for the two new tests — `GET /api/italian/progress` returns 404 (route not added yet).

- [ ] **Step 4: Implement the progress endpoint**

Inside `createItalianRouter` in `server/src/italian.ts`, before `return router;` add:
```ts
  router.get("/progress", async (req: AuthedRequest, res) => {
    const fromParam = dateParam(req.query.from);
    const anchor = fromParam ? new Date(`${fromParam}T00:00:00`) : new Date();

    const logs = await prisma.practiceLog.findMany({
      where: { ownerId: req.userId },
      select: { createdAt: true }
    });
    const completed = await prisma.lesson.findMany({
      where: { ownerId: req.userId, completedAt: { not: null } },
      select: { createdAt: true, completedAt: true }
    });

    const activeDays = new Set<string>(logs.map((l) => dateKeyFor(l.createdAt)));
    for (const lesson of completed) activeDays.add(dateKeyFor(lesson.completedAt!));

    let streak = 0;
    const cursor = new Date(anchor);
    while (activeDays.has(dateKeyFor(cursor))) {
      streak++;
      cursor.setDate(cursor.getDate() - 1);
    }

    res.json({ streak, totalLessons: completed.length, totalPractice: logs.length });
  });
```

- [ ] **Step 5: Run the server tests to verify they pass**

```bash
npm test --workspace server
```
Expected: all tests PASS, including both new progress tests.

- [ ] **Step 6: Typecheck, lint (server)**

```bash
npm run typecheck
npm run lint --workspace server
```
Expected: no errors.

- [ ] **Step 7: Add the client progress API call**

In `client/src/italianApi.ts` update the `shared` import and append:
```ts
import type { ItalianLesson, ItalianCorrection, ItalianProgress } from "shared";
```
```ts
export async function getProgress(token: string): Promise<ItalianProgress> {
  const res = await authedFetch(token, "/api/italian/progress");
  return json<ItalianProgress>(res);
}
```

- [ ] **Step 8: Wire progress into App and the lesson chip**

In `client/src/App.tsx`:
- Import `getProgress` from `./italianApi`.
- Add state and an effect:
```tsx
const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

useEffect(() => {
  if (!token) return;
  getProgress(token)
    .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
    .catch(() => setProgress(null));
}, [token]);
```
- Pass the values to the lesson view:
```tsx
<ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />
```
This activates the progress chip already rendered by `ItalianLesson` (Task 4.4): `"{streak} day streak · {totalLessons} lessons completed"`.

- [ ] **Step 9: Typecheck, lint (client)**

```bash
npm run typecheck
npm run lint --workspace client
```
Expected: no errors.

- [ ] **Step 10: Manual verification**

With both servers running: complete a lesson's phrases (Task 4.4 flow), reload — the chip shows `1 day streak · 1 lessons completed`. Practicing on a second consecutive day increments the streak; skipping a day resets it to 0.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: italian progress streak and lesson completion tracking"
```

---

## Phase 4 Self-Review

- **Spec coverage (§3.3 Italian tutor with voice):**
  - Daily lesson generator ("lesson of the day") → Task 4.1 (`GET /lesson/today`, cached per date in the `Lesson` model).
  - Speak English → tutor teaches Italian → Tasks 4.2 (`POST /translate` via `LLMProvider`), 4.3 (browser STT), 4.4 (tutor panel, STT en-US → translation → TTS).
  - Correction flow with retry → Tasks 4.2 (`POST /check` → structured `ItalianCorrection` via `LLMProvider`) and 4.4 (recite flow, feedback UI, "Try again").
  - SpeechProvider as browser-native Web Speech API + `speechSynthesis` (spec §5) → Task 4.3.
  - LLMProvider abstraction (spec §5) → Task 4.1 extends the canonical `server/src/providers/llm.ts` (built by Phase 3) with `complete()` and `extractJson`, preserving `generateFlashcards`/`generateQuiz`, `getLLMProvider`, and the `LLM_PROVIDER=http|mock` env switch; free default (no key → deterministic canned fallback), swappable to a free-tier key via the existing `LLM_HTTP_KEY`; no paid keys.
  - Streaks/progress (feeds §3.5 focus digest) → Task 4.5 plus `PracticeLog` recording in Task 4.2.
  - Honest limitation (spec §7, "decent, not Siri-grade") → carried in the Goal, Global Constraints, Task 4.3 manual-verification note.

- **Placeholder scan:** No TODOs/TBDs/"implement later"; every step carries runnable code, expected test output, a typecheck/lint gate, and a commit. The only non-unit-tested surfaces are the browser-API calls (`SpeechRecognition`, `speechSynthesis`), which are covered by explicit manual verification steps in Tasks 4.3, 4.4, and 4.5 — their pure logic (`preferVoice`, `normalizeTranscript`) has real unit tests.

- **Type consistency:**
  - `LLMProvider.complete(prompt: string): Promise<string>` is added in Task 4.1 and consumed identically by `generateLessonBody` (4.1), `checkAttempt` (4.2), and `translateToItalian` (4.2); Phase 3's `generateFlashcards`/`generateQuiz` are untouched, and `llmMockProvider()`/`llmHttpProvider()`/`getLLMProvider()` keep their names and behavior.
  - `createItalianRouter(provider?: LLMProvider)` signature constant across all five tasks; the default-mounted `italianRouter` uses `getLLMProvider()`.
  - `dateKeyFor(d: Date): string` defined in Task 4.1, used by lesson/check/translate defaults (4.2) and the progress anchor (4.5).
  - `ItalianLesson` shape (`id/date/title/tip/vocab/phrases`) defined in shared Task 4.1 and used by the server mapping (`lessonFromRow`) and the client API/UI (4.4) without drift.
  - `ItalianCorrection` fields (`isCorrect/phraseEnglish/targetItalian/userAttempt/mistakeType/hint/correctPhrase`) defined in shared Task 4.2 and used by `tryParseCorrection`/`fallbackCorrection` (server) and `ItalianLesson.tsx` (client) with identical names.
  - `ItalianProgress` (`streak/totalLessons/totalPractice`) defined in shared Task 4.5 and used by the progress endpoint and `getProgress`.
  - Client speech helper names (`startListening`, `speak`, `preferVoice`, `normalizeTranscript`, `warmVoices`, `isSpeechRecognitionSupported`) defined in Task 4.3 and consumed verbatim by `ItalianLesson.tsx` (4.4).
  - `checkAttempt(token, { dateKey, phraseIndex, userAttempt })` and `translateText(token, text)` in `italianApi.ts` (4.4) match the server `POST /check` / `POST /translate` request bodies.
  - `PracticeLog`/`Lesson.completedAt` are created in the Task 4.1 schema, populated by the Task 4.2 `check` handler, and read by Task 4.5's endpoint.

- **Deliberate choices recorded for the executor:** `vocab`/`phrases` are stored as JSON strings in SQLite (no native arrays); the provider path always degrades to deterministic canned content when `complete()` throws (e.g., no `LLM_HTTP_KEY`) or returns unparseable text, which keeps the suite hermetic even if a dev `.env` has a real key set — Italian router tests inject `llmMockProvider()`/`mockComplete` directly and never depend on the app-mounted default provider except for the unauthenticated-401 check; the fallback correction is a strict normalized string comparison and only runs when no live LLM is reachable or its output is unparseable — the LLM remains the authority when present.