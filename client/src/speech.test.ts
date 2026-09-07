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
