import { describe, expect, test, vi } from "vitest";
import { createVisionProvider } from "./vision";
import { createGeminiProvider, parseGeminiItems, VisionError, type GeminiFetch } from "./visionGemini";

const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3]);
const reply = (text: string, ok = true, status = 200): GeminiFetch =>
  vi.fn(async () => ({ ok, status, text: async () => JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }) }));

describe("gemini vision provider", () => {
  test("parses fenced JSON and sums calories", async () => {
    const f = reply('```json\n{"items":[{"foodName":"Rice","estimatedGrams":150,"estimatedCalories":200},{"foodName":"Egg","estimatedGrams":"50","estimatedCalories":78.4}]}\n```');
    const p = createGeminiProvider({ apiKey: "k", fetchImpl: f });
    const r = await p.estimateCalories(jpeg, "a.jpg");
    expect(r.provider).toBe("gemini");
    expect(r.totalCalories).toBe(278);
    expect(r.items[1].estimatedGrams).toBe(50);
    const call = (f as any).mock.calls[0];
    expect(call[0]).toContain("gemini-2.5-flash");
    expect(call[1].headers["x-goog-api-key"]).toBe("k");
  });
  test("uses VISION_MODEL via factory", async () => {
    const f = reply('{"items":[]}');
    const p = await createVisionProvider({ VISION_PROVIDER: "gemini", GEMINI_API_KEY: "k", VISION_MODEL: "gemini-x" } as any, f);
    await p.estimateCalories(jpeg, "a.jpg");
    expect((f as any).mock.calls[0][0]).toContain("gemini-x");
  });
  test("falls back to stub without key", async () => {
    const p = await createVisionProvider({ VISION_PROVIDER: "gemini" } as any);
    expect(p.name).toBe("stub");
  });
  test("error mapping", async () => {
    const mk = (f: GeminiFetch) => createGeminiProvider({ apiKey: "k", fetchImpl: f });
    await expect(mk(reply("x", false, 429)).estimateCalories(jpeg, "a.jpg")).rejects.toMatchObject({ status: 503 });
    await expect(mk(reply("x", false, 500)).estimateCalories(jpeg, "a.jpg")).rejects.toMatchObject({ status: 502 });
    await expect(mk(reply("no json")).estimateCalories(jpeg, "a.jpg")).rejects.toBeInstanceOf(VisionError);
    await expect(mk(async () => { throw new Error("net"); }).estimateCalories(jpeg, "a.jpg")).rejects.toMatchObject({ status: 502 });
    await expect(mk(reply("{}")).estimateCalories(Buffer.alloc(8 * 1024 * 1024, 1), "a.jpg")).rejects.toMatchObject({ status: 413 });
    await expect(mk(reply("{}")).estimateCalories(Buffer.from("zz"), "a.txt")).rejects.toMatchObject({ status: 415 });
  });
  test("parse drops bad items and clamps", () => {
    const items = parseGeminiItems('{"items":[{"foodName":"","estimatedCalories":1},{"foodName":"X","estimatedGrams":-5,"estimatedCalories":99999}, 3]}');
    expect(items).toEqual([{ foodName: "X", estimatedGrams: 0, estimatedCalories: 5000 }]);
  });
});
