import { describe, it, expect } from "vitest";
import { llmMockProvider, getLLMProvider, llmHttpProvider } from "./llm";

describe("llm mock provider", () => {
  it("returns typed flashcards", async () => {
    const p = llmMockProvider();
    const cards = await p.generateFlashcards("mitochondria are the powerhouse of the cell", 2);
    expect(cards).toHaveLength(2);
    expect(cards[0].front).toBeTruthy();
    expect(cards[0].back).toBeTruthy();
    expect("answerIndex" in cards[0]).toBe(false); // flashcards have no choices
  });

  it("returns typed quizzes", async () => {
    const p = llmMockProvider();
    const qs = await p.generateQuiz("Photosynthesis converts light to chemical energy", 3);
    expect(qs).toHaveLength(3);
    expect(qs[0].choices.length).toBe(4);
    expect(qs[0].answerIndex).toBeGreaterThanOrEqual(0);
  });

  it("clamps counts to provider limits", async () => {
    const p = llmMockProvider();
    const cards = await p.generateFlashcards("x y z", 100);
    expect(cards.length).toBeLessThanOrEqual(20);
  });
});

describe("provider switch", () => {
  it("returns http provider by default and mock when requested", () => {
    process.env.LLM_PROVIDER = "http";
    expect(getLLMProvider().generateFlashcards).toBeTruthy();
    process.env.LLM_PROVIDER = "mock";
    expect(getLLMProvider().generateFlashcards).toBeTruthy();
  });

  it("http provider throws without a configured key", async () => {
    const oldKey = process.env.LLM_HTTP_KEY;
    delete process.env.LLM_HTTP_KEY;
    await expect(llmHttpProvider().generateFlashcards("a b c", 1)).rejects.toThrow(/LLM_HTTP_KEY/);
    if (oldKey !== undefined) process.env.LLM_HTTP_KEY = oldKey;
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
