import { describe, expect, test } from "vitest";
import { ITALIAN_COURSE } from "./italianCourse";

const lessons = ITALIAN_COURSE.units.flatMap((u) => u.lessons);
const exercises = lessons.flatMap((l) => l.exercises);

describe("Italian course content", () => {
  test("unit, lesson and exercise ids are unique", () => {
    const unitIds = ITALIAN_COURSE.units.map((u) => u.id);
    expect(new Set(unitIds).size).toBe(unitIds.length);
    expect(new Set(lessons.map((l) => l.id)).size).toBe(lessons.length);
    expect(new Set(exercises.map((e) => e.id)).size).toBe(exercises.length);
  });

  test("every lesson has exercises and a title", () => {
    for (const lesson of lessons) {
      expect(lesson.title.trim(), lesson.id).not.toBe("");
      expect(lesson.exercises.length, lesson.id).toBeGreaterThanOrEqual(4);
    }
  });

  test("every unit ends with a review test", () => {
    for (const unit of ITALIAN_COURSE.units) {
      expect(unit.lessons.length, unit.id).toBeGreaterThanOrEqual(4);
      const last = unit.lessons[unit.lessons.length - 1];
      expect(last.kind, unit.id).toBe("test");
      expect(last.title.startsWith("Test:"), unit.id).toBe(true);
      expect(last.exercises.length, unit.id).toBeGreaterThanOrEqual(8);
      expect(unit.lessons.filter((l) => l.kind === "test"), unit.id).toHaveLength(1);
    }
  });

  test("choice exercises have four distinct choices and a valid answer", () => {
    for (const ex of exercises) {
      if (ex.kind !== "choice") continue;
      expect(ex.choices.length, ex.id).toBe(4);
      expect(new Set(ex.choices).size, ex.id).toBe(4);
      expect(ex.answerIndex, ex.id).toBeGreaterThanOrEqual(0);
      expect(ex.answerIndex, ex.id).toBeLessThan(ex.choices.length);
    }
  });

  test("listening exercises say something", () => {
    for (const ex of exercises) {
      if (ex.kind === "choice" && ex.direction === "listen") {
        expect(ex.speak?.trim(), ex.id).toBeTruthy();
      }
    }
  });

  test("match exercises have four pairs with no repeats", () => {
    for (const ex of exercises) {
      if (ex.kind !== "match") continue;
      expect(ex.pairs.length, ex.id).toBe(4);
      expect(new Set(ex.pairs.map((p) => p.left)).size, ex.id).toBe(4);
      expect(new Set(ex.pairs.map((p) => p.right)).size, ex.id).toBe(4);
    }
  });
});
