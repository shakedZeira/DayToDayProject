import { Router } from "express";
import type { ProgressResponse } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { currentStreak, dayKey } from "./streak";
import { dayRange, todayKey } from "./digest";
import { caloriesBetween, doneTasksPerDay, italianActiveDays, localDayKey, RECURRING, taskCompletionDays } from "./aggregates";

/** Same result as computeTaskProgress, but counted/bucketed in Postgres instead of loading every task. */
export async function taskProgressFromDb(ownerId: string, now: Date) {
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const firstDay = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6);
  const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const [total, completedThisWeek, plannedThisWeek, completionDays, perDay] = await Promise.all([
    prisma.task.count({ where: { ownerId } }),
    prisma.task.count({ where: { ownerId, status: "DONE", completedAt: { gte: weekAgo } } }),
    prisma.task.count({
      where: { ownerId, OR: [{ dueAt: { gte: weekAgo, lte: now } }, RECURRING] }
    }),
    taskCompletionDays(ownerId),
    doneTasksPerDay(ownerId, firstDay, endOfToday)
  ]);
  const completedByDay: number[] = [];
  for (let i = 6; i >= 0; i--) {
    completedByDay.push(perDay.get(localDayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - i))) ?? 0);
  }
  return {
    total,
    completedThisWeek,
    completionRateWeek: plannedThisWeek === 0 ? 0 : Math.min(1, completedThisWeek / plannedThisWeek),
    streak: currentStreak(completionDays, now),
    completedByDay
  };
}

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

  const [taskProgress, workoutCount, targetRow, caloriesToday, activeDays, totalLessons, lessonsThisWeek] =
    await Promise.all([
      taskProgressFromDb(ownerId, now),
      prisma.workout.count({ where: { ownerId, date: { gte: weekAgo } } }),
      prisma.dailyTarget.findUnique({ where: { ownerId } }),
      caloriesBetween(ownerId, range.start, range.end),
      italianActiveDays(ownerId),
      prisma.lesson.count({ where: { ownerId, completedAt: { not: null } } }),
      prisma.lesson.count({ where: { ownerId, completedAt: { not: null, gte: weekAgo } } })
    ]);

  const body: ProgressResponse = {
    date: todayKey(now),
    tasks: taskProgress,
    health: {
      workoutsThisWeek: workoutCount,
      workoutsTarget: 2,
      caloriesToday,
      calorieTarget: targetRow?.calories ?? 2000
    },
    italian: {
      streak: currentStreak(activeDays, now),
      totalLessons,
      lessonsThisWeek
    }
  };
  res.json(body);
});
