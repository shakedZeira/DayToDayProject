import { Router } from "express";
import type { DigestResponse, DigestTaskItem } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { dueOn } from "./tasks";
import { currentStreak } from "./streak";
import { italianActiveDays, RECURRING } from "./aggregates";

export interface TaskRow {
  id: string;
  title: string;
  category: string | null;
  recurrence: string | null;
  status: string;
  dueAt: Date | null;
  completedAt: Date | null;
}

export function todayKey(now: Date): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function dayRange(now: Date = new Date()): { start: Date; end: Date } {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  return { start, end };
}

export function workoutReminder(workoutsThisWeek: number, lastWorkoutDaysAgo: number | null): string | null {
  if (workoutsThisWeek >= 2) return null;
  if (lastWorkoutDaysAgo === null) return "No workouts this week yet — aim for 2.";
  if (lastWorkoutDaysAgo >= 3) return "3+ days since your last workout — schedule a session.";
  return null;
}

export interface DigestInput {
  today: string;
  tasksToday: TaskRow[];
  workoutsThisWeek: number;
  lastWorkoutDaysAgo: number | null;
  lessonTitle: string | null;
  activeDays: Date[];
  totalLessons: number;
}

export function buildDigest(input: DigestInput): DigestResponse {
  const tasksToday: DigestTaskItem[] = input.tasksToday.map((t) => ({
    id: t.id,
    title: t.title,
    category: t.category,
    recurrence: t.recurrence ?? "none",
    status: t.status as "PENDING" | "DONE"
  }));
  return {
    date: input.today,
    topTask: tasksToday.find((t) => t.status === "PENDING") ?? null,
    tasksToday,
    health: {
      workoutsThisWeek: input.workoutsThisWeek,
      lastWorkoutDaysAgo: input.lastWorkoutDaysAgo,
      reminder: workoutReminder(input.workoutsThisWeek, input.lastWorkoutDaysAgo)
    },
    italian: {
      lessonTitle: input.lessonTitle,
      streak: currentStreak(input.activeDays, new Date(input.today + "T12:00:00")),
      totalLessons: input.totalLessons
    }
  };
}

export const digestRouter = Router();
digestRouter.use(requireAuth);

digestRouter.get("/today", async (req: AuthedRequest, res) => {
  const now = new Date();
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const ownerId = req.userId!;

  const { start, end } = dayRange(now);

  // Candidate tasks are narrowed in the DB: recurring ones (any status) or pending one-offs due today.
  // dueOn() then only has to resolve the weekly day-of-week rule in JS.
  const [candidateTasks, workoutsThisWeek, lastWorkout, lessonToday, activeDays, totalLessons] =
    await Promise.all([
      prisma.task.findMany({
        where: {
          ownerId,
          OR: [
            RECURRING,
            { AND: [{ status: { not: "DONE" } }, { dueAt: { gte: start, lt: end } }, { OR: [{ recurrence: null }, { recurrence: "none" }] }] }
          ]
        }
      }),
      prisma.workout.count({ where: { ownerId, date: { gte: weekAgo } } }),
      prisma.workout.findFirst({ where: { ownerId }, orderBy: { date: "desc" }, select: { date: true } }),
      prisma.lesson.findFirst({ where: { ownerId, date: todayKey(now) }, select: { title: true } }),
      italianActiveDays(ownerId),
      prisma.lesson.count({ where: { ownerId, completedAt: { not: null } } })
    ]);

  const tasksToday = candidateTasks.filter((t) => dueOn(t, now));
  const lastWorkoutDaysAgo = lastWorkout
    ? Math.max(0, Math.floor((now.getTime() - lastWorkout.date.getTime()) / 86_400_000))
    : null;

  res.json(
    buildDigest({
      today: todayKey(now),
      tasksToday,
      workoutsThisWeek,
      lastWorkoutDaysAgo,
      lessonTitle: lessonToday?.title ?? null,
      activeDays,
      totalLessons
    })
  );
});
