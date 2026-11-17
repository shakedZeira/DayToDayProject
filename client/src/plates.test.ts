import { describe, it, expect } from "vitest";
import { calculatePlates } from "./plates";
import { toggleLinkWithPrevious, normalizeGroups, isSupersetContinuation, supersetLabel } from "./supersets";

describe("calculatePlates", () => {
  it("loads 100kg on a 20kg bar as 25+15 per side", () => {
    const r = calculatePlates(100);
    expect(r.perSide).toEqual([{ plate: 25, count: 1 }, { plate: 15, count: 1 }]);
    expect(r.achievableKg).toBe(100);
    expect(r.remainderKg).toBe(0);
  });
  it("handles fractional plates and repeats", () => {
    expect(calculatePlates(142.5).perSide).toEqual([
      { plate: 25, count: 2 }, { plate: 10, count: 1 }, { plate: 1.25, count: 1 },
    ]);
    expect(calculatePlates(60).perSide).toEqual([{ plate: 20, count: 1 }]);
  });
  it("reports the remainder when the target is not loadable", () => {
    const r = calculatePlates(61);
    expect(r.achievableKg).toBe(60);
    expect(r.remainderKg).toBe(1);
  });
  it("is invalid below the bar and supports custom bar and plates", () => {
    expect(calculatePlates(15).valid).toBe(false);
    expect(calculatePlates(20).perSide).toEqual([]);
    expect(calculatePlates(35, 15, [5]).perSide).toEqual([{ plate: 5, count: 2 }]);
  });
});

describe("supersets", () => {
  it("links with the previous exercise, creating a group", () => {
    expect(toggleLinkWithPrevious([null, null, null], 1)).toEqual([1, 1, null]);
    expect(toggleLinkWithPrevious([1, 1, null], 2)).toEqual([1, 1, 1]);
  });
  it("unlinks and drops one-member groups", () => {
    expect(toggleLinkWithPrevious([1, 1, null], 1)).toEqual([null, null, null]);
    expect(toggleLinkWithPrevious([1, 1, 1], 2)).toEqual([1, 1, null]);
  });
  it("ignores the first exercise", () => {
    expect(toggleLinkWithPrevious([null, null], 0)).toEqual([null, null]);
  });
  it("normalizes and detects continuation", () => {
    expect(normalizeGroups([5, 5, 9, null, 9])).toEqual([1, 1, 2, null, 2]);
    expect(isSupersetContinuation([1, 1, null], 0)).toBe(true);
    expect(isSupersetContinuation([1, 1, null], 1)).toBe(false);
    expect(supersetLabel(2)).toBe("Superset B");
    expect(supersetLabel(null)).toBeNull();
  });
});
