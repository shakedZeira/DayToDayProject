import { describe, it, expect } from "vitest";
import { llmMockProvider, getLLMProvider, getChatProvider, llmHttpProvider } from "./llm";

describe("provider switch", () => {
  it("returns http provider by default and mock when requested", () => {
    process.env.LLM_PROVIDER = "http";
    expect(getLLMProvider().complete).toBeTruthy();
    process.env.LLM_PROVIDER = "mock";
    expect(getLLMProvider().complete).toBeTruthy();
  });

});

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

describe("chat method (extended for the Italian tutor chatbot phase)", () => {
  it("mock provider's chat resolves to a deterministic string", async () => {
    const p = llmMockProvider();
    const a = await p.chat("system", [{ role: "user", content: "ciao" }]);
    const b = await p.chat("system", [{ role: "user", content: "ciao" }]);
    expect(typeof a).toBe("string");
    expect(a).toBe(b);
  });

  it("http provider's chat omits Authorization when no key is configured (keyless)", async () => {
    const oldChatKey = process.env.LLM_CHAT_KEY;
    const oldHttpKey = process.env.LLM_HTTP_KEY;
    process.env.LLM_CHAT_KEY = "";
    delete process.env.LLM_HTTP_KEY;
    const original = global.fetch;
    let captured: { headers?: Record<string, string> } | undefined;
    global.fetch = (async (_url: string, init: { headers?: Record<string, string> }) => {
      captured = init;
      return { ok: true, json: async () => ({ choices: [{ message: { content: "ok" } }] }) } as never;
    }) as typeof fetch;
    try {
      const out = await llmHttpProvider().chat("system", [{ role: "user", content: "ciao" }]);
      expect(out).toBe("ok");
      expect(captured?.headers?.Authorization).toBeUndefined();
    } finally {
      global.fetch = original;
      if (oldChatKey === undefined) delete process.env.LLM_CHAT_KEY;
      else process.env.LLM_CHAT_KEY = oldChatKey;
      if (oldHttpKey !== undefined) process.env.LLM_HTTP_KEY = oldHttpKey;
    }
  });
});

describe("chatStream method (hands-free voice chat phase)", () => {
  it("mock provider's chatStream emits non-empty delta pieces that concatenate to a non-empty string", async () => {
    const pieces: string[] = [];
    await llmMockProvider().chatStream("system", [{ role: "user", content: "ciao" }], (d) => pieces.push(d));
    expect(pieces.length).toBeGreaterThanOrEqual(1);
    expect(pieces.every((p) => typeof p === "string" && p.length > 0)).toBe(true);
    expect(pieces.join("")).not.toBe("");
  });
});

describe("chat provider selection (keyless free tier)", () => {
  it("getChatProvider stays mock when LLM_PROVIDER=mock and no chat url", async () => {
    const oldProvider = process.env.LLM_PROVIDER;
    const oldUrl = process.env.LLM_CHAT_URL;
    process.env.LLM_PROVIDER = "mock";
    delete process.env.LLM_CHAT_URL;
    try {
      await expect(getChatProvider().complete("x")).resolves.toBe('{"fallback":"canned"}');
    } finally {
      if (oldProvider === undefined) delete process.env.LLM_PROVIDER;
      else process.env.LLM_PROVIDER = oldProvider;
      if (oldUrl !== undefined) process.env.LLM_CHAT_URL = oldUrl;
    }
  });

  it("getChatProvider uses http when a dedicated chat url is set even with LLM_PROVIDER=mock", async () => {
    const oldProvider = process.env.LLM_PROVIDER;
    const oldUrl = process.env.LLM_CHAT_URL;
    const oldKey = process.env.LLM_HTTP_KEY;
    process.env.LLM_PROVIDER = "mock";
    process.env.LLM_CHAT_URL = "https://example.invalid/chat";
    delete process.env.LLM_HTTP_KEY;
    try {
      await expect(getChatProvider().complete("x")).rejects.toThrow(/LLM_HTTP_KEY/);
    } finally {
      if (oldProvider === undefined) delete process.env.LLM_PROVIDER;
      else process.env.LLM_PROVIDER = oldProvider;
      if (oldUrl === undefined) delete process.env.LLM_CHAT_URL;
      else process.env.LLM_CHAT_URL = oldUrl;
      if (oldKey !== undefined) process.env.LLM_HTTP_KEY = oldKey;
    }
  });
});
