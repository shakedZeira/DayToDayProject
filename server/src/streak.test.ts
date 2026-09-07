import { test, expect } from "vitest";
import { currentStreak } from "./streak";

function d(y: number, m: number, day: number): Date {
  return new Date(y, m - 1, day, 12);
}

test("currentStreak is 0 with no active dates", () => {
  expect(currentStreak([], d(2026, 9, 1))).toBe(0);
});

test("currentStreak counts consecutive days ending at the anchor", () => {
  const dates = [d(2026, 9, 1), d(2026, 8, 31), d(2026, 8, 30)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(3);
});

test("currentStreak breaks on a gap", () => {
  const dates = [d(2026, 9, 1), d(2026, 8, 29)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(1);
});

test("currentStreak returns 0 when the anchor day itself is inactive", () => {
  const dates = [d(2026, 8, 31), d(2026, 8, 30)];
  expect(currentStreak(dates, d(2026, 9, 1))).toBe(0);
});
