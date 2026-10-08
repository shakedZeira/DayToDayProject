import type { ItalianAnswer, ItalianCourse, ItalianExercise, ItalianLessonDef } from "shared";

export function normalizeAnswer(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
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

export function flatLessons(course: ItalianCourse): ItalianLessonDef[] {
  return course.units.flatMap((u) => u.lessons);
}

export function isLessonUnlocked(course: ItalianCourse, completed: Set<string>, lessonId: string): boolean {
  const lessons = flatLessons(course);
  const idx = lessons.findIndex((l) => l.id === lessonId);
  if (idx <= 0) return idx === 0;
  return completed.has(lessons[idx - 1].id);
}

export function nextLessonId(course: ItalianCourse, completed: Set<string>): string | null {
  return flatLessons(course).find((l) => !completed.has(l.id))?.id ?? null;
}

export type LessonStatus = "completed" | "unlocked" | "locked";

export function lessonStatuses(course: ItalianCourse, completed: Set<string>): Record<string, LessonStatus> {
  const out: Record<string, LessonStatus> = {};
  for (const l of flatLessons(course)) {
    out[l.id] = completed.has(l.id) ? "completed" : isLessonUnlocked(course, completed, l.id) ? "unlocked" : "locked";
  }
  return out;
}
