import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { ITALIAN_COURSE } from "./content/italianCourse";
import { gradeExercise } from "./italianPathGrade";
import { emitGoalSignals } from "./goalsAuto";
import { currentStreak, dayKey } from "./streak";
import type {
  ItalianAnswer,
  ItalianExercise,
  ItalianLessonDef,
  ItalianPathProgress,
  ItalianSubmitResult
} from "shared";

const EXERCISE_XP = 1;
const FIRST_COMPLETION_XP = 10;
const REPLAY_XP = 5;

const ALL_LESSONS: ItalianLessonDef[] = ITALIAN_COURSE.units.flatMap((u) => u.lessons);
const EXERCISE_INDEX: Map<string, { lessonId: string; exercise: ItalianExercise }> = new Map(
  ALL_LESSONS.flatMap((l) =>
    l.exercises.map(
      (e) => [e.id, { lessonId: l.id, exercise: e }] as [string, { lessonId: string; exercise: ItalianExercise }]
    )
  )
);

export function findLesson(lessonId: string): ItalianLessonDef | undefined {
  return ALL_LESSONS.find((l) => l.id === lessonId);
}

async function buildProgress(ownerId: string): Promise<ItalianPathProgress> {
  const [completions, attempts, wrongAttempts] = await Promise.all([
    prisma.italianLessonCompletion.findMany({
      where: { ownerId },
      select: { lessonId: true, completedAt: true }
    }),
    prisma.italianExerciseAttempt.findMany({
      where: { ownerId, isCorrect: true },
      select: { exerciseId: true, createdAt: true }
    }),
    prisma.italianExerciseAttempt.findMany({
      where: { ownerId, isCorrect: false },
      select: { exerciseId: true, createdAt: true }
    })
  ]);
  const correctIds = new Set(attempts.map((a) => a.exerciseId));
  const reviewDue = wrongAttempts
    .filter((a) => !correctIds.has(a.exerciseId))
    .map((a) => ({ lessonId: EXERCISE_INDEX.get(a.exerciseId)?.lessonId ?? "", exerciseId: a.exerciseId }))
    .filter((r, i, arr) => arr.findIndex((x) => x.exerciseId === r.exerciseId) === i);

  const attemptAgg = await prisma.italianExerciseAttempt.aggregate({
    where: { ownerId },
    _sum: { xpAwarded: true }
  });
  const completionAgg = await prisma.italianLessonCompletion.aggregate({
    where: { ownerId },
    _sum: { xpAwarded: true }
  });
  const xp = (attemptAgg._sum.xpAwarded ?? 0) + (completionAgg._sum.xpAwarded ?? 0);

  const activeDates = [...attempts.map((a) => a.createdAt), ...completions.map((c) => c.completedAt)];
  const now = new Date();
  const todayActive = activeDates.some((d) => dayKey(d) === dayKey(now));
  const anchor = todayActive ? now : new Date(now.getTime() - 86_400_000);
  const streak = activeDates.length === 0 ? 0 : currentStreak(activeDates, anchor);

  return {
    xp,
    streak,
    completedLessonIds: completions.map((c) => c.lessonId),
    totalLessons: ALL_LESSONS.length,
    reviewDue
  };
}

export const italianPathRouter = Router();
italianPathRouter.use(requireAuth);

italianPathRouter.get("/course", (_req, res) => {
  res.json(ITALIAN_COURSE);
});

italianPathRouter.get("/progress", async (req: AuthedRequest, res) => {
  res.json(await buildProgress(req.userId!));
});

italianPathRouter.post("/lesson/:lessonId/submit", async (req: AuthedRequest, res) => {
  const lesson = findLesson(req.params.lessonId);
  if (!lesson) return res.status(404).json({ error: "not found" });

  const rawAnswers = req.body?.answers;
  if (!Array.isArray(rawAnswers)) return res.status(400).json({ error: "answers required" });

  const answers: ItalianAnswer[] = [];
  const lessonExerciseIds = new Set(lesson.exercises.map((e) => e.id));
  for (const a of rawAnswers) {
    if (!a || typeof a.exerciseId !== "string" || !lessonExerciseIds.has(a.exerciseId)) {
      return res.status(400).json({ error: "unknown exercise" });
    }
    if (a.kind !== "choice" && a.kind !== "type" && a.kind !== "match") {
      return res.status(400).json({ error: "invalid answer" });
    }
    answers.push(a as ItalianAnswer);
  }

  const ownerId = req.userId!;
  const previouslyCorrect = new Set(
    (
      await prisma.italianExerciseAttempt.findMany({
        where: { ownerId, exerciseId: { in: [...lessonExerciseIds] }, isCorrect: true },
        select: { exerciseId: true }
      })
    ).map((r) => r.exerciseId)
  );

  const results: { exerciseId: string; isCorrect: boolean }[] = [];
  let attemptXp = 0;
  for (const answer of answers) {
    const ex = EXERCISE_INDEX.get(answer.exerciseId)!.exercise;
    const isCorrect = gradeExercise(ex, answer);
    const xp = isCorrect && !previouslyCorrect.has(answer.exerciseId) ? EXERCISE_XP : 0;
    attemptXp += xp;
    previouslyCorrect.add(answer.exerciseId);
    results.push({ exerciseId: answer.exerciseId, isCorrect });
    await prisma.italianExerciseAttempt.create({
      data: {
        ownerId,
        lessonId: lesson.id,
        exerciseId: answer.exerciseId,
        isCorrect,
        userAnswer: JSON.stringify(answer),
        xpAwarded: xp
      }
    });
  }

  const correctNow = await prisma.italianExerciseAttempt.findMany({
    where: { ownerId, lessonId: lesson.id, isCorrect: true },
    select: { exerciseId: true }
  });
  const allCorrect = lesson.exercises.every((e) => correctNow.some((r) => r.exerciseId === e.id));

  const existing = await prisma.italianLessonCompletion.findFirst({
    where: { ownerId, lessonId: lesson.id }
  });
  let completionXp = 0;
  let firstCompletion = false;
  if (allCorrect) {
    if (existing) {
      completionXp = REPLAY_XP;
      await prisma.italianLessonCompletion.update({
        where: { id: existing.id },
        data: { xpAwarded: existing.xpAwarded + REPLAY_XP }
      });
    } else {
      completionXp = FIRST_COMPLETION_XP;
      firstCompletion = true;
      await prisma.italianLessonCompletion.create({
        data: { ownerId, lessonId: lesson.id, xpAwarded: FIRST_COMPLETION_XP }
      });
    }
  }

  const xpGained = attemptXp + completionXp;
  if (firstCompletion) {
    await emitGoalSignals(ownerId, { italian_lesson: 1, italian_words: 1 });
  }
  await emitGoalSignals(ownerId, { italian_xp: xpGained });

  const progress = await buildProgress(ownerId);
  const body: ItalianSubmitResult = { results, xpGained, lessonCompleted: firstCompletion, progress };
  res.status(201).json(body);
});
