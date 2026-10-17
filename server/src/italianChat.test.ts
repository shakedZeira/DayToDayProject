import express from "express";
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { createItalianChatRouter, looksLikeMeta } from "./italianChat";
import { llmMockProvider, type ChatMessage, type LLMProvider } from "./providers/llm";

function mockChat(output: string): LLMProvider {
  return { ...llmMockProvider(), chat: async () => output };
}

function wrapRouter(router: express.Router): express.Express {
  const mini = express();
  mini.use(express.json());
  mini.use(router);
  return mini;
}

let token = "";

beforeEach(async () => {
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "italianchat@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("chat: rejects unauthenticated requests", async () => {
  const res = await request(app)
    .post("/api/italian/chat")
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(401);
});

test("chat: invalid messages payloads return 400 with an error", async () => {
  const router = createItalianChatRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const post = (body: Record<string, unknown>) =>
    request(mini).post("/chat").set("Authorization", `Bearer ${token}`).send(body);

  const missing = await post({});
  expect(missing.status).toBe(400);
  expect(typeof missing.body.error).toBe("string");

  const notArray = await post({ messages: "ciao" });
  expect(notArray.status).toBe(400);
  expect(typeof notArray.body.error).toBe("string");

  const empty = await post({ messages: [] });
  expect(empty.status).toBe(400);
  expect(typeof empty.body.error).toBe("string");

  const badRole = await post({ messages: [{ role: "bot", content: "ciao" }] });
  expect(badRole.status).toBe(400);
  expect(typeof badRole.body.error).toBe("string");

  const emptyContent = await post({ messages: [{ role: "user", content: "" }] });
  expect(emptyContent.status).toBe(400);
  expect(typeof emptyContent.body.error).toBe("string");

  const tooMany = await post({
    messages: Array.from({ length: 41 }, () => ({ role: "user", content: "ciao" }))
  });
  expect(tooMany.status).toBe(400);
  expect(typeof tooMany.body.error).toBe("string");
});

test("chat: parses a JSON envelope into reply, correction and translation", async () => {
  const provider = mockChat(
    JSON.stringify({
      reply: "Come stai?",
      correction: "Say 'Come stai?' instead of 'Come sei?'",
      translation: "How are you?"
    })
  );
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Come sei?" }] });
  expect(res.status).toBe(200);
  expect(res.body).toEqual({
    reply: "Come stai?",
    correction: "Say 'Come stai?' instead of 'Come sei?'",
    translation: "How are you?"
  });
});

test("chat: non-JSON provider output falls back to raw text", async () => {
  const provider = mockChat("Mi chiamo Marco.");
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Mi chiamo Marco" }] });
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ reply: "Mi chiamo Marco.", correction: "", translation: "" });
});

test("chat: provider failure returns 200 with a canned Italian reply", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chat: async () => {
      throw new Error("upstream unavailable");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  expect(typeof res.body.reply).toBe("string");
  expect(res.body.reply.length).toBeGreaterThan(0);
});

test("chat: passes multi-turn messages through to the provider", async () => {
  let capturedSystem = "";
  let capturedMessages: ChatMessage[] = [];
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chat: async (system, messages) => {
      capturedSystem = system;
      capturedMessages = messages;
      return '{"reply":"Perfetto!","correction":"","translation":"Perfect!"}';
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const messages: ChatMessage[] = [
    { role: "user", content: "Ciao!" },
    { role: "assistant", content: "Ciao! Come stai?" },
    { role: "user", content: "Io sono bene, grazie" }
  ];
  const res = await request(mini)
    .post("/chat")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages });
  expect(res.status).toBe(200);
  expect(res.body.reply).toBe("Perfetto!");
  expect(capturedSystem.length).toBeGreaterThan(0);
  expect(capturedMessages).toEqual(messages);
});

test("chat/stream: rejects unauthenticated requests", async () => {
  const res = await request(app)
    .post("/api/italian/chat/stream")
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(401);
});

test("chat/stream: invalid messages payloads return 400 with an error", async () => {
  const router = createItalianChatRouter(llmMockProvider());
  const mini = wrapRouter(router);
  const post = (body: Record<string, unknown>) =>
    request(mini).post("/chat/stream").set("Authorization", `Bearer ${token}`).send(body);

  const missing = await post({});
  expect(missing.status).toBe(400);
  expect(typeof missing.body.error).toBe("string");

  const empty = await post({ messages: [] });
  expect(empty.status).toBe(400);
  expect(typeof empty.body.error).toBe("string");

  const badRole = await post({ messages: [{ role: "bot", content: "ciao" }] });
  expect(badRole.status).toBe(400);
  expect(typeof badRole.body.error).toBe("string");

  const emptyContent = await post({ messages: [{ role: "user", content: "" }] });
  expect(emptyContent.status).toBe(400);
  expect(typeof emptyContent.body.error).toBe("string");
});

test("chat/stream: emits ndjson deltas and a final done line", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("Ciao ");
      onDelta("come stai?");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  expect(res.headers["content-type"]).toContain("application/x-ndjson");
  expect(res.text).toContain('"delta":"Ciao "');
  expect(res.text).toContain('"delta":"come stai?"');
  expect(res.text).toContain('"done":true');
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines[0]).toEqual({ delta: "Ciao ", lang: "it" });
  expect(lines[1]).toEqual({ delta: "come stai?", lang: "it" });
  expect(lines[lines.length - 1]).toEqual({ done: true });
});

test("chat/stream: tags English portions and strips the <en>/</en> markers", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("Ciao! ");
      onDelta("<en>How are you?</en> ");
      onDelta("Bene?");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines).toEqual([
    { delta: "Ciao! ", lang: "it" },
    { delta: "How are you? ", lang: "en" },
    { delta: "Bene?", lang: "it" },
    { done: true }
  ]);
  for (const line of lines) {
    if (typeof line.delta === "string") {
      expect(line.delta).not.toContain("<en>");
      expect(line.delta).not.toContain("</en>");
    }
  }
  expect(res.text).not.toContain("<en>");
  expect(res.text).not.toContain("</en>");
});

test("chat/stream: tolerates a tag split across provider deltas", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("ciao <e");
      onDelta("n>hi</en> okay");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines).toEqual([
    { delta: "ciao ", lang: "it" },
    { delta: "hi", lang: "en" },
    { delta: " okay", lang: "it" },
    { done: true }
  ]);
  for (const line of lines) {
    if (typeof line.delta === "string") {
      expect(line.delta).not.toContain("<");
      expect(line.delta).not.toContain("<en>");
    }
  }
});

test("chat/stream: defaults to lang it when no tags appear", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("Ciao, come stai oggi?");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines).toEqual([{ delta: "Ciao, come stai oggi?", lang: "it" }, { done: true }]);
});

test("chat/stream: provider failure before any delta returns 200 with a canned delta and done", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async () => {
      throw new Error("upstream unavailable");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines.length).toBeGreaterThanOrEqual(2);
  expect(typeof lines[0].delta).toBe("string");
  expect(lines[0].delta.length).toBeGreaterThan(0);
  expect(lines[0].lang).toBe("it");
  expect(lines[lines.length - 1]).toEqual({ done: true });
});

test("chat/stream: provider failure mid-stream just ends with done", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("Ciao!");
      throw new Error("connection reset");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "Ciao!" }] });
  expect(res.status).toBe(200);
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  expect(lines[0]).toEqual({ delta: "Ciao!", lang: "it" });
  expect(lines[lines.length - 1]).toEqual({ done: true });
  expect(res.text.match(/"delta"/g)?.length).toBe(1);
});

test("looksLikeMeta: flags leaked reasoning, allows normal teaching lines", () => {
  expect(looksLikeMeta("We need to keep the lesson moving. The user said 'grazie mille'.")).toBe(true);
  expect(looksLikeMeta("Step 1: say the Italian word. Step 2: give the meaning.")).toBe(true);
  expect(looksLikeMeta("I will now respond with a new word.")).toBe(true);
  expect(looksLikeMeta("Ciao! It means 'hi'. Ripeti: ciao?")).toBe(false);
  expect(looksLikeMeta("Buongiorno. It means 'good morning'.")).toBe(false);
});

test("chat: replaces leaked-reasoning reply with a canned teaching reply", async () => {
  const provider = mockChat(
    JSON.stringify({
      reply: "We need to keep the lesson moving. The user said 'grazie mille'.",
      correction: "",
      translation: ""
    })
  );
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "grazie mille" }] });
  expect(res.status).toBe(200);
  expect(res.body.reply).not.toContain("We need");
  expect(res.body.reply).not.toContain("The user");
  expect(res.body.reply.length).toBeGreaterThan(0);
});

test("chat/stream: replaces leaked-reasoning output with a canned teaching turn", async () => {
  const provider: LLMProvider = {
    ...llmMockProvider(),
    chatStream: async (_s, _m, onDelta) => {
      onDelta("We need to keep the lesson moving. ");
      onDelta("The user said 'grazie mille'. ");
      onDelta("We can respond with a new word.");
    }
  };
  const router = createItalianChatRouter(provider);
  const mini = wrapRouter(router);
  const res = await request(mini)
    .post("/chat/stream")
    .set("Authorization", `Bearer ${token}`)
    .send({ messages: [{ role: "user", content: "grazie mille" }] });
  expect(res.status).toBe(200);
  expect(res.text).not.toContain("We need");
  expect(res.text).not.toContain("The user");
  const lines = res.text.split("\n").filter(Boolean).map((l) => JSON.parse(l));
  for (const line of lines) {
    if (typeof line.delta === "string") expect(line.delta.length).toBeGreaterThan(0);
  }
  expect(lines[lines.length - 1]).toEqual({ done: true });
});
