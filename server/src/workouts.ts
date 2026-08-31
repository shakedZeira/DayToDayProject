import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { ProgressiveSuggestion, WorkoutSetInput } from "shared";

export const workoutsRouter = Router();
workoutsRouter.use(requireAuth);

export const DEFAULT_INCREMENT_KG = 2.5;
const TARGET_SETS = 3;
const TARGET_REPS = 8;

interface CompletedSet {
  exercise: string;
  weightKg: number;
  reps: number;
}

export async function progressiveSuggestion(
  completedSets: CompletedSet[],
  incrementKg = DEFAULT_INCREMENT_KG
): Promise<ProgressiveSuggestion[]> {
  const groups = new Map<string, CompletedSet[]>();
  for (const s of completedSets) {
    const arr = groups.get(s.exercise) ?? [];
    arr.push(s);
    groups.set(s.exercise, arr);
  }

  const suggestions: ProgressiveSuggestion[] = [];
  for (const [exercise, setGroup] of groups) {
    const hit = setGroup.length >= TARGET_SETS && setGroup.every((s) => s.reps >= TARGET_REPS);
    const currentWeightKg = Math.max(...setGroup.map((s) => s.weightKg));
    suggestions.push({
      exercise,
      currentWeightKg,
      suggestedNextKg: hit ? currentWeightKg + incrementKg : currentWeightKg,
      reason: hit ? "completed_target" : "not_yet",
    });
  }

  suggestions.sort((a, b) => a.exercise.localeCompare(b.exercise));
  return suggestions;
}

export async function historyFor(ownerId: string) {
  return prisma.workout.findMany({
    where: { ownerId },
    orderBy: { date: "asc" },
    include: {
      sets: {
        orderBy: { createdAt: "asc" },
        select: { exercise: true, weightKg: true, reps: true },
      },
    },
  });
}

workoutsRouter.get("/", async (req: AuthedRequest, res) => {
  res.json(await historyFor(req.userId!));
});

workoutsRouter.post("/", async (req: AuthedRequest, res) => {
  const { title, date, notes } = req.body ?? {};
  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  const workout = await prisma.workout.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      date: date ? new Date(date) : new Date(),
      notes: typeof notes === "string" ? notes : null,
    },
  });

  const goal = await prisma.weeklyGoal.findFirst({
    where: { ownerId: req.userId, autoSource: "workout" },
  });
  if (goal) {
    await prisma.goalEvent.create({
      data: { ownerId: req.userId!, goalId: goal.id, date: new Date(), count: 1, source: "workout" },
    });
  }

  res.status(201).json(workout);
});

workoutsRouter.get("/:id", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
    include: { sets: { orderBy: { createdAt: "asc" } } },
  });
  if (!workout) return res.status(404).json({ error: "not found" });
  res.json(workout);
});

function isValidSet(s: WorkoutSetInput): boolean {
  return (
    typeof s === "object" &&
    s !== null &&
    typeof s.exercise === "string" &&
    s.exercise.trim() !== "" &&
    typeof s.weightKg === "number" &&
    Number.isFinite(s.weightKg) &&
    s.weightKg > 0 &&
    typeof s.reps === "number" &&
    Number.isFinite(s.reps) &&
    s.reps > 0
  );
}

workoutsRouter.post("/:id/sets", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!workout) return res.status(404).json({ error: "not found" });

  const { sets } = req.body ?? {};
  if (!Array.isArray(sets) || sets.length === 0) {
    return res.status(400).json({ error: "sets required" });
  }
  for (const s of sets) {
    if (!isValidSet(s)) {
      return res.status(400).json({ error: "invalid set payload" });
    }
  }

  const created = [];
  for (const s of sets) {
    created.push(
      await prisma.workoutSet.create({
        data: {
          ownerId: req.userId!,
          workoutId: workout.id,
          exercise: s.exercise.trim(),
          weightKg: s.weightKg,
          reps: Math.floor(s.reps),
        },
      })
    );
  }
  res.status(201).json(created);
});

workoutsRouter.post("/:id/progressive", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
    include: { sets: true },
  });
  if (!workout) return res.status(404).json({ error: "not found" });
  res.json(await progressiveSuggestion(workout.sets));
});
