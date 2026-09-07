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
