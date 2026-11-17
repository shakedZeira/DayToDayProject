import { describe, expect, it } from "vitest";
import { levenshtein, scoreSpeech } from "./speechScore";

describe("scoreSpeech", () => {
  it("is 100 for exact and accent/case/punctuation-insensitive matches", () => {
    expect(scoreSpeech("buongiorno", "Buongiorno!").score).toBe(100);
    expect(scoreSpeech("e una citta", "È una città.").score).toBe(100);
  });
  it("gives partial credit for near misses and lists missing words", () => {
    const near = scoreSpeech("buon giorno", "buongiorno");
    expect(near.score).toBeGreaterThan(60);
    const part = scoreSpeech("come stai", "come stai oggi");
    expect(part.missing).toEqual(["oggi"]);
    expect(part.score).toBeLessThan(100);
    expect(part.score).toBeGreaterThan(50);
  });
  it("is low for unrelated or empty speech", () => {
    expect(scoreSpeech("pizza", "arrivederci").score).toBeLessThan(30);
    expect(scoreSpeech("", "ciao").score).toBe(0);
    expect(scoreSpeech("ciao", "").score).toBe(0);
  });
  it("levenshtein basics", () => {
    expect(levenshtein("kitten", "sitting")).toBe(3);
    expect(levenshtein("", "abc")).toBe(3);
  });
});
