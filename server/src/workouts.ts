import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { ProgressiveSuggestion, WorkoutSetInput } from "shared";

export const workoutsRouter = Router();
workoutsRouter.use(requireAuth);

export const DEFAULT_INCREMENT_KG = 2.5;
const TARGET_SETS = 3;
const TARGET_REPS = 8;

export const ALLOWED_SET_TYPES = ["working", "warmup", "dropset", "failure"];

async function isUsableExerciseId(exerciseId: string, userId: string): Promise<boolean> {
  const ex = await prisma.exercise.findUnique({ where: { id: exerciseId } });
  if (!ex) return false;
  return ex.ownerId === null || ex.ownerId === userId;
}

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
    include: {
      sets: { orderBy: { createdAt: "asc" } },
      exercises: {
        orderBy: { order: "asc" },
        include: { sets: { orderBy: { order: "asc" } } },
      },
    },
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

workoutsRouter.post("/:id/exercises", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!workout) return res.status(404).json({ error: "not found" });

  const { exerciseId, exerciseName, notes } = req.body ?? {};
  let resolvedName: string | undefined;

  if (exerciseId) {
    if (!(await isUsableExerciseId(exerciseId, req.userId!))) {
      return res.status(400).json({ error: "invalid exerciseId" });
    }
    const ex = await prisma.exercise.findUnique({ where: { id: exerciseId } });
    resolvedName = ex!.name;
  } else if (typeof exerciseName === "string" && exerciseName.trim()) {
    resolvedName = exerciseName.trim();
  } else {
    return res.status(400).json({ error: "exerciseId or exerciseName required" });
  }

  const count = await prisma.workoutExercise.count({
    where: { workoutId: workout.id },
  });

  const xe = await prisma.workoutExercise.create({
    data: {
      ownerId: req.userId!,
      workoutId: workout.id,
      exerciseId: exerciseId ?? null,
      exerciseName: resolvedName!,
      order: count + 1,
      notes: typeof notes === "string" ? notes : null,
    },
  });

  res.status(201).json({ ...xe, sets: [] });
});

workoutsRouter.post("/:id/exercises/:xeId/sets", async (req: AuthedRequest, res) => {
  const workout = await prisma.workout.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!workout) return res.status(404).json({ error: "not found" });

  const xe = await prisma.workoutExercise.findFirst({
    where: { id: req.params.xeId, workoutId: workout.id, ownerId: req.userId },
  });
  if (!xe) return res.status(404).json({ error: "exercise not found" });

  const { weightKg, reps, setType, restSeconds, rpe } = req.body ?? {};
  if (typeof weightKg !== "number" || !Number.isFinite(weightKg) || weightKg <= 0) {
    return res.status(400).json({ error: "invalid weightKg" });
  }
  if (typeof reps !== "number" || !Number.isFinite(reps) || reps < 1 || Math.floor(reps) !== reps) {
    return res.status(400).json({ error: "invalid reps" });
  }
  if (setType !== undefined && !ALLOWED_SET_TYPES.includes(setType)) {
    return res.status(400).json({ error: "invalid setType" });
  }
  if (restSeconds !== undefined && (typeof restSeconds !== "number" || !Number.isFinite(restSeconds) || restSeconds < 0 || Math.floor(restSeconds) !== restSeconds)) {
    return res.status(400).json({ error: "invalid restSeconds" });
  }
  if (rpe !== undefined && (typeof rpe !== "number" || !Number.isFinite(rpe) || rpe < 0)) {
    return res.status(400).json({ error: "invalid rpe" });
  }

  const setCount = await prisma.workoutSet.count({
    where: { workoutExerciseId: xe.id },
  });

  const ws = await prisma.workoutSet.create({
    data: {
      ownerId: req.userId!,
      workoutId: workout.id,
      workoutExerciseId: xe.id,
      exercise: xe.exerciseName,
      order: setCount + 1,
      setType: setType ?? "working",
      weightKg,
      reps: Math.floor(reps),
      restSeconds: restSeconds != null ? Math.floor(restSeconds) : null,
      rpe: rpe ?? null,
      notes: typeof req.body?.notes === "string" ? req.body.notes : null,
    },
  });

  res.status(201).json(ws);
});
