import type { ItalianAnswer, ItalianExercise } from "shared";

export function normalizeAnswer(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[.,!?;:"'’]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function gradeExercise(ex: ItalianExercise, answer: ItalianAnswer): boolean {
  switch (ex.kind) {
    case "choice":
      return answer.kind === "choice" && answer.choiceIndex === ex.answerIndex;
    case "type":
      return (
        answer.kind === "type" &&
        ex.accepted.some((a) => normalizeAnswer(a) === normalizeAnswer(answer.text))
      );
    case "match": {
      if (answer.kind !== "match") return false;
      const key = (p: { left: string; right: string }) =>
        `${normalizeAnswer(p.left)}|${normalizeAnswer(p.right)}`;
      if (answer.pairs.length !== ex.pairs.length) return false;
      const have = new Set(answer.pairs.map(key));
      return ex.pairs.every((p) => have.has(key(p)));
    }
  }
}
