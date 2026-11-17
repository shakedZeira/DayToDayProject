import { Router } from "express";
import type { WeeklyReviewResponse } from "shared";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { caloriesPerDay, doneTasksPerDay, italianActiveDays, localDayKey, workoutDays } from "./aggregates";
import { currentStreak } from "./streak";

export const weeklyReviewRouter = Router();
weeklyReviewRouter.use(requireAuth);

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

// GET /api/review/weekly[?date=YYYY-MM-DD] — the 7 local days ending on `date` (default today).
weeklyReviewRouter.get("/weekly", async (req: AuthedRequest, res) => {
  const ownerId = req.userId!;
  let now = new Date();
  if (req.query.date !== undefined) {
    const d = typeof req.query.date === "string" && DATE_RE.test(req.query.date) ? req.query.date : null;
    if (!d || Number.isNaN(Date.parse(`${d}T00:00:00Z`))) return res.status(400).json({ error: "date must be YYYY-MM-DD" });
    now = new Date(`${d}T12:00:00`);
  }
  const first = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const days: string[] = [];
  for (let i = 6; i >= 0; i--) days.push(localDayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - i)));

  const [taskPerDay, wDays, kcalPerDay, targetRow, lessonsCompleted, xp, prs, activeDays] = await Promise.all([
    doneTasksPerDay(ownerId, first, end),
    workoutDays(ownerId, first, end),
    caloriesPerDay(ownerId, first, end),
    prisma.dailyTarget.findUnique({ where: { ownerId } }),
    prisma.lesson.count({ where: { ownerId, completedAt: { gte: first, lt: end } } }),
    prisma.italianLessonCompletion.aggregate({
      where: { ownerId, completedAt: { gte: first, lt: end } },
      _sum: { xpAwarded: true }
    }),
    prisma.personalRecord.findMany({
      where: { ownerId, date: { gte: first, lt: end } },
      orderBy: [{ date: "asc" }, { exerciseName: "asc" }]
    }),
    italianActiveDays(ownerId)
  ]);

  const workoutCount = await prisma.workout.count({ where: { ownerId, date: { gte: first, lt: end } } });
  const completedByDay = days.map((d) => taskPerDay.get(d) ?? 0);
  const byDay = days
    .filter((d) => kcalPerDay.has(d))
    .map((d) => ({ date: d, calories: Math.round(kcalPerDay.get(d) as number) }));
  const targetCalories = targetRow?.calories ?? 2000;
  const avgCalories = byDay.length ? Math.round(byDay.reduce((s, d) => s + d.calories, 0) / byDay.length) : 0;

  const body: WeeklyReviewResponse = {
    from: days[0],
    to: days[6],
    tasks: { completed: completedByDay.reduce((a, b) => a + b, 0), completedByDay },
    workouts: { count: workoutCount, target: 2, days: wDays },
    nutrition: {
      targetCalories,
      daysLogged: byDay.length,
      avgCalories,
      diffFromTarget: byDay.length ? avgCalories - targetCalories : 0,
      byDay
    },
    italian: {
      lessonsCompleted,
      xpEarned: xp._sum.xpAwarded ?? 0,
      streak: currentStreak(activeDays, now)
    },
    personalRecords: prs.map((p) => ({
      exerciseName: p.exerciseName,
      repRange: p.repRange,
      weight: p.weight,
      e1rm: p.e1rm,
      date: localDayKey(p.date)
    }))
  };
  res.json(body);
});
