import { Router } from "express";
import type { ProgressResponse } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { currentStreak, dayKey } from "./streak";
import { dayRange, todayKey } from "./digest";

// Reuses the Phase 2 calorie formula: kcal = grams / 100 * caloriesPer100, rounded.
export function consumedCalories(meals: { grams: number; food: { caloriesPer100: number } }[]): number {
  return Math.round(meals.reduce((sum, m) => sum + m.food.caloriesPer100 * (m.grams / 100), 0));
}

export interface TaskProgressRow {
  status: "PENDING" | "DONE";
  completedAt: Date | null;
  createdAt: Date;
  dueAt: Date | null;
  recurrence: string;
}

export function computeTaskProgress(
  tasks: TaskProgressRow[],
  now: Date
): { total: number; completedThisWeek: number; completionRateWeek: number; streak: number; completedByDay: number[] } {
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const completedThisWeek = tasks.filter(
    (t) => t.status === "DONE" && t.completedAt != null && t.completedAt >= weekAgo
  ).length;
  const plannedThisWeek = tasks.filter(
    (t) =>
      (t.dueAt != null && t.dueAt >= weekAgo && t.dueAt <= now) ||
      (t.recurrence !== "none" && t.recurrence != null)
  ).length;
  const completionRateWeek = plannedThisWeek === 0 ? 0 : Math.min(1, completedThisWeek / plannedThisWeek);
  const streak = currentStreak(
    tasks.filter((t) => t.completedAt != null).map((t) => t.completedAt as Date),
    now
  );
  const completedByDay: number[] = [];
  for (let i = 6; i >= 0; i--) {
    const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    completedByDay.push(
      tasks.filter((t) => t.status === "DONE" && t.completedAt != null && dayKey(t.completedAt) === dayKey(day)).length
    );
  }
  return { total: tasks.length, completedThisWeek, completionRateWeek, streak, completedByDay };
}

export const progressRouter = Router();
progressRouter.use(requireAuth);

progressRouter.get("/", async (req: AuthedRequest, res) => {
  const now = new Date();
  const range = dayRange(now);
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const ownerId = req.userId!;

  const [tasks, workoutCount, targetRow, meals, pdfCount, highlightCount, flashcardCount, pdfsInProgress, practiceLogs, completedLessons, lessonsThisWeek] =
    await Promise.all([
      prisma.task.findMany({
        where: { ownerId },
        select: { status: true, completedAt: true, createdAt: true, dueAt: true, recurrence: true }
      }),
      prisma.workout.count({ where: { ownerId, date: { gte: weekAgo } } }),
      prisma.dailyTarget.findUnique({ where: { ownerId } }),
      prisma.mealLog.findMany({
        where: { ownerId, date: { gte: range.start, lt: range.end } },
        include: { food: { select: { caloriesPer100: true } } }
      }),
      prisma.pdf.count({ where: { ownerId } }),
      prisma.highlight.count({ where: { ownerId } }),
      prisma.flashcard.count({ where: { ownerId } }),
      prisma.pdf.count({ where: { ownerId, readProgress: { gt: 0, lt: 1 } } }),
      prisma.practiceLog.findMany({ where: { ownerId }, select: { createdAt: true } }),
      prisma.lesson.findMany({ where: { ownerId, completedAt: { not: null } }, select: { completedAt: true, createdAt: true } }),
      prisma.lesson.count({ where: { ownerId, completedAt: { not: null, gte: weekAgo } } })
    ]);

  const activeDays = [
    ...practiceLogs.map((l) => l.createdAt),
    ...completedLessons.map((l) => l.completedAt as Date)
  ];
  const taskProgress = computeTaskProgress(
    tasks.map((t) => ({ ...t, status: t.status as "PENDING" | "DONE", recurrence: t.recurrence ?? "none" })),
    now
  );
  const body: ProgressResponse = {
    date: todayKey(now),
    tasks: taskProgress,
    health: {
      workoutsThisWeek: workoutCount,
      workoutsTarget: 2,
      caloriesToday: consumedCalories(meals),
      calorieTarget: targetRow?.calories ?? 2000
    },
    study: {
      pdfs: pdfCount,
      highlights: highlightCount,
      flashcards: flashcardCount,
      pdfsInProgress
    },
    italian: {
      streak: currentStreak(activeDays, now),
      totalLessons: completedLessons.length,
      lessonsThisWeek
    }
  };
  res.json(body);
});
