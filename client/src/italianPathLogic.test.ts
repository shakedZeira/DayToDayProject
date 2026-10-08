import { describe, it, expect } from "vitest";
import { gradeExercise, isLessonUnlocked, lessonStatuses, nextLessonId } from "./italianPathLogic";
import type { ItalianCourse } from "shared";

const course: ItalianCourse = {
  language: "it",
  units: [
    {
      id: "u1",
      title: "Basics",
      lessons: [
        { id: "u1l1", title: "Greetings", exercises: [] },
        { id: "u1l2", title: "Intros", exercises: [] },
        { id: "u1l3", title: "Polite", exercises: [] }
      ]
    }
  ]
};

describe("gradeExercise", () => {
  it("grades choice answers", () => {
    const ex = { id: "e", kind: "choice" as const, direction: "en_to_it" as const, prompt: "hi", choices: ["ciao", "grazie"], answerIndex: 0 };
    expect(gradeExercise(ex, { exerciseId: "e", kind: "choice", choiceIndex: 0 })).toBe(true);
    expect(gradeExercise(ex, { exerciseId: "e", kind: "choice", choiceIndex: 1 })).toBe(false);
  });

  it("grades type answers ignoring case and accents", () => {
    const ex = { id: "e", kind: "type" as const, prompt: "p", accepted: ["caffè"] };
    expect(gradeExercise(ex, { exerciseId: "e", kind: "type", text: "  CAFFE! " })).toBe(true);
    expect(gradeExercise(ex, { exerciseId: "e", kind: "type", text: "te" })).toBe(false);
  });
});

describe("path logic", () => {
  it("unlocks first lesson and completed successors", () => {
    expect(isLessonUnlocked(course, new Set(), "u1l1")).toBe(true);
    expect(isLessonUnlocked(course, new Set(), "u1l2")).toBe(false);
    expect(isLessonUnlocked(course, new Set(["u1l1"]), "u1l2")).toBe(true);
  });

  it("finds the next incomplete lesson", () => {
    expect(nextLessonId(course, new Set())).toBe("u1l1");
    expect(nextLessonId(course, new Set(["u1l1", "u1l2"]))).toBe("u1l3");
    expect(nextLessonId(course, new Set(["u1l1", "u1l2", "u1l3"]))).toBeNull();
  });

  it("computes lesson statuses for the path", () => {
    const s = lessonStatuses(course, new Set(["u1l1"]));
    expect(s.u1l1).toBe("completed");
    expect(s.u1l2).toBe("unlocked");
    expect(s.u1l3).toBe("locked");
  });
});
