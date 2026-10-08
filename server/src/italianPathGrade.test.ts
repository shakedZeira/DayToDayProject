import { describe, it, expect } from "vitest";
import { normalizeAnswer, gradeExercise } from "./italianPathGrade";
import type { ItalianAnswer, ItalianExercise } from "shared";

const choice: ItalianExercise = {
  id: "e1", kind: "choice", direction: "en_to_it", prompt: "hello",
  choices: ["ciao", "grazie", "arrivederci", "scusi"], answerIndex: 0
};
const typeEx: ItalianExercise = { id: "e2", kind: "type", prompt: "t", accepted: ["Buongiorno", "buongiorno"] };
const matchEx: ItalianExercise = {
  id: "e3", kind: "match",
  pairs: [{ left: "ciao", right: "hello" }, { left: "grazie", right: "thank you" }]
};

describe("normalizeAnswer", () => {
  it("lowercases, strips accents and punctuation", () => {
    expect(normalizeAnswer("  BuonGiorno! ")).toBe("buongiorno");
    expect(normalizeAnswer("caffè.")).toBe("caffe");
    expect(normalizeAnswer("mi   chiamo")).toBe("mi chiamo");
  });
});

describe("gradeExercise", () => {
  it("grades choice answers", () => {
    const right: ItalianAnswer = { exerciseId: "e1", kind: "choice", choiceIndex: 0 };
    const wrong: ItalianAnswer = { exerciseId: "e1", kind: "choice", choiceIndex: 2 };
    expect(gradeExercise(choice, right)).toBe(true);
    expect(gradeExercise(choice, wrong)).toBe(false);
  });

  it("grades type answers against any accepted variant", () => {
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "type", text: "buongiorno!!" })).toBe(true);
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "type", text: "buonasera" })).toBe(false);
    expect(gradeExercise(typeEx, { exerciseId: "e2", kind: "choice", choiceIndex: 0 })).toBe(false);
  });

  it("grades match answers as set equality regardless of order", () => {
    const a: ItalianAnswer = {
      exerciseId: "e3", kind: "match",
      pairs: [{ left: "grazie", right: "thank you" }, { left: "ciao", right: "hello" }]
    };
    expect(gradeExercise(matchEx, a)).toBe(true);
    const b: ItalianAnswer = {
      exerciseId: "e3", kind: "match",
      pairs: [{ left: "ciao", right: "hi" }, { left: "grazie", right: "thank you" }]
    };
    expect(gradeExercise(matchEx, b)).toBe(false);
  });
});
