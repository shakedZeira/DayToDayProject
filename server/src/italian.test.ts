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
