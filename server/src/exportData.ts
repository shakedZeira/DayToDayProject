import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const exportRouter = Router();
exportRouter.use(requireAuth);

/** Everything the signed-in user owns, as one JSON document (no password hash, no photo bytes). */
export async function buildExport(ownerId: string) {
  const where = { ownerId };
  const [
    user, tasks, goals, goalEvents, workouts, mealLogs, dailyTarget, profile, routines,
    progressions, personalRecords, lessons, practiceLogs, italianCompletions, italianAttempts,
    settings, recipes, recipeFavorites, customExercises
  ] = await Promise.all([
    prisma.user.findUnique({ where: { id: ownerId }, select: { id: true, email: true, createdAt: true } }),
    prisma.task.findMany({ where }),
    prisma.weeklyGoal.findMany({ where }),
    prisma.goalEvent.findMany({ where }),
    prisma.workout.findMany({ where, include: { exercises: { include: { sets: true } }, sets: true } }),
    prisma.mealLog.findMany({ where, include: { food: { select: { name: true, caloriesPer100: true } } } }),
    prisma.dailyTarget.findUnique({ where: { ownerId } }),
    prisma.userProfile.findUnique({ where: { ownerId } }),
    prisma.routine.findMany({ where, include: { days: { include: { exercises: true } } } }),
    prisma.exerciseProgression.findMany({ where }),
    prisma.personalRecord.findMany({ where }),
    prisma.lesson.findMany({ where }),
    prisma.practiceLog.findMany({ where }),
    prisma.italianLessonCompletion.findMany({ where }),
    prisma.italianExerciseAttempt.findMany({ where }),
    prisma.setting.findMany({ where }),
    prisma.recipe.findMany({ where }),
    prisma.recipeFavorite.findMany({ where }),
    prisma.exercise.findMany({ where })
  ]);
  return {
    exportedAt: new Date().toISOString(),
    version: 1,
    user,
    tasks,
    goals: goals.map((g) => ({ ...g, events: goalEvents.filter((e) => e.goalId === g.id) })),
    workouts,
    customExercises,
    mealLogs,
    dailyTarget,
    profile,
    routines,
    progressions,
    personalRecords,
    italian: { lessons, practiceLogs, completions: italianCompletions, attempts: italianAttempts },
    recipes,
    recipeFavorites,
    settings: Object.fromEntries(settings.map((s) => [s.key, s.value]))
  };
}

exportRouter.get("/", async (req: AuthedRequest, res) => {
  const body = await buildExport(req.userId!);
  const stamp = new Date().toISOString().slice(0, 10);
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Content-Disposition", `attachment; filename="daytoday-export-${stamp}.json"`);
  res.send(JSON.stringify(body, null, 2));
});
