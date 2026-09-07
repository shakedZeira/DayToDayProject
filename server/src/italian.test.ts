import express from "express";
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { createItalianRouter } from "./italian";
import { llmMockProvider, type LLMProvider } from "./providers/llm";

function mockComplete(output: string): LLMProvider {
  return { ...llmMockProvider(), complete: async () => output };
}

function wrapRouter(router: express.Router): express.Express {
  const mini = express();
  mini.use(express.json());
  mini.use(router);
  return mini;
}

let token = "";
let ownerId = "";

beforeEach(async () => {
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
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
  const mini = wrapRouter(router);
  const res = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.date).toBe("2026-08-30");
  expect(res.body.title).toBe("Daily Italian: greetings");
  expect(res.body.vocab.length).toBeGreaterThan(0);
  expect(res.body.phrases.length).toBeGreaterThan(0);
  const id = res.body.id as string;

  const again = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  expect(again.status).toBe(200);
  expect(again.body.id).toBe(id);
});

test("lesson/today with force=1 regenerates", async () => {
  const router = createItalianRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const first = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const second = await request(mini)
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
  const mini = wrapRouter(router);
  const res = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.title).toBe("Al bar");
  expect(res.body.phrases).toHaveLength(2);
});

test("mock provider with unparseable output falls back to the canned lesson", async () => {
  const router = createItalianRouter(mockComplete("this is not json at all"));
  const mini = wrapRouter(router);
  const res = await request(mini)
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

test("check: mock provider correction is parsed into structured feedback", async () => {
  const correctionJson = JSON.stringify({
    isCorrect: true,
    mistakeType: "correct",
    hint: "",
    correctPhrase: "Come stai?"
  });
  const router = createItalianRouter(mockComplete(correctionJson));
  const mini = wrapRouter(router);
  await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30", force: "1" })
    .set("Authorization", `Bearer ${token}`);

  const res = await request(mini)
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
  const mini = wrapRouter(router);
  await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const res = await request(mini)
    .post("/check")
    .send({ dateKey: "2026-08-30", phraseIndex: 99, userAttempt: "ciao" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(404);
});

test("check: one correct phrase does not complete the lesson", async () => {
  const router = createItalianRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const lesson = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const first = lesson.body.phrases[0] as { italian: string };
  await request(mini)
    .post("/check")
    .send({ dateKey: "2026-08-30", phraseIndex: 0, userAttempt: first.italian })
    .set("Authorization", `Bearer ${token}`);
  const stored = await prisma.lesson.findFirst({ where: { ownerId, date: "2026-08-30" } });
  expect(stored?.completedAt).toBeFalsy();
});

test("check: correct recital of every phrase completes the lesson", async () => {
  const router = createItalianRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const lesson = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const phrases = lesson.body.phrases as Array<{ italian: string }>;
  for (let i = 0; i < phrases.length; i++) {
    const res = await request(mini)
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
  const mini = wrapRouter(router);
  await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);

  const known = await request(mini)
    .post("/translate")
    .send({ dateKey: "2026-08-30", text: "How are you?" })
    .set("Authorization", `Bearer ${token}`);
  expect(known.status).toBe(200);
  expect(known.body.italian).toBe("Come stai?");

  const unknown = await request(mini)
    .post("/translate")
    .send({ dateKey: "2026-08-30", text: "I need the train station" })
    .set("Authorization", `Bearer ${token}`);
  expect(unknown.body.italian).toBe("Non ho capito, puoi ripetere?");
});

test("check: completing the lesson increments auto-source weekly goals", async () => {
  const goal = await prisma.weeklyGoal.create({
    data: {
      ownerId,
      title: "Italian lesson this week",
      targetCount: 3,
      autoSource: "italian_lesson"
    }
  });
  await prisma.weeklyGoal.create({
    data: {
      ownerId,
      title: "Words this week",
      targetCount: 10,
      autoSource: "italian_words"
    }
  });

  const router = createItalianRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const lesson = await request(mini)
    .get("/lesson/today")
    .query({ date: "2026-08-30" })
    .set("Authorization", `Bearer ${token}`);
  const phrases = lesson.body.phrases as Array<{ italian: string }>;
  for (let i = 0; i < phrases.length; i++) {
    await request(mini)
      .post("/check")
      .send({ dateKey: "2026-08-30", phraseIndex: i, userAttempt: phrases[i].italian })
      .set("Authorization", `Bearer ${token}`);
  }

  const events = await prisma.goalEvent.findMany({ where: { goalId: goal.id } });
  expect(events).toHaveLength(1);
  expect(events[0].source).toBe("italian_lesson");

  const wordsEvents = await prisma.goalEvent.findMany({
    where: { goal: { ownerId, autoSource: "italian_words" } }
  });
  expect(wordsEvents).toHaveLength(1);
});
