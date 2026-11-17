import { describe, expect, test } from "vitest";
import { INITIAL_SRS, sm2 } from "./italianSrs";

describe("sm2", () => {
  test("intervals grow 1, 6, then by ease", () => {
    const a = sm2(INITIAL_SRS, 4);
    expect(a.intervalDays).toBe(1);
    const b = sm2(a, 4);
    expect(b.intervalDays).toBe(6);
    const c = sm2(b, 5);
    expect(c.intervalDays).toBe(Math.round(6 * c.ease));
    expect(c.repetitions).toBe(3);
  });
  test("lapse resets and floors ease", () => {
    let s = { ease: 1.35, intervalDays: 20, repetitions: 5 };
    s = sm2(s, 0);
    expect(s).toMatchObject({ intervalDays: 1, repetitions: 0 });
    expect(s.ease).toBe(1.3);
  });
  test("quality 4 keeps ease, 5 raises it", () => {
    expect(sm2(INITIAL_SRS, 4).ease).toBeCloseTo(2.5);
    expect(sm2(INITIAL_SRS, 5).ease).toBeCloseTo(2.6);
  });
});
