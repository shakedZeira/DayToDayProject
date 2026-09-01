import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { ProgressionUpdateInput } from "shared";

export const progressionRouter = Router();
progressionRouter.use(requireAuth);

export const REP_RANGES = [1, 3, 5, 8, 10, 12];

export function e1rm(weightKg: number, reps: number): number {
  return weightKg * (1 + reps / 30);
}

export function matchRepRange(reps: number): number {
  if (reps < 1) return 1;
  if (reps >= 12) return 12;
  let pick = 1;
  for (const r of REP_RANGES) {
    if (r <= reps) pick = r;
    else break;
  }
  return pick;
}

export function progressingTargetsComplete(
  targetSets: number,
  targetReps: number,
  workingSets: { reps: number }[]
): boolean {
  return workingSets.length >= targetSets && workingSets.every((s) => s.reps >= targetReps);
}

async function resolveExerciseId(ownerId: string, exerciseName: string): Promise<string | null> {
  const ex = await prisma.exercise.findFirst({
    where: {
      OR: [{ ownerId: null, name: exerciseName }, { ownerId, name: exerciseName }],
    },
    select: { id: true },
  });
  return ex?.id ?? null;
}

export async function updateProgression(
  ownerId: string,
  exerciseName: string,
  workingSets: { weightKg: number; reps: number; setType?: string }[]
): Promise<void> {
  const ws = workingSets.filter((s) => (s.setType ?? "working") === "working");
  if (ws.length === 0) return;

  const maxWeightSet = ws.reduce((a, b) => (b.weightKg > a.weightKg ? b : a));
  const rr = matchRepRange(maxWeightSet.reps);
  const bestE1rm = Math.max(...ws.map((s) => e1rm(s.weightKg, s.reps)));
  const exerciseId = await resolveExerciseId(ownerId, exerciseName);

  const existingPr = await prisma.personalRecord.findUnique({
    where: {
      ownerId_exerciseName_repRange: { ownerId, exerciseName, repRange: rr },
    },
  });
  if (!existingPr || existingPr.weight < maxWeightSet.weightKg) {
    await prisma.personalRecord.upsert({
      where: {
        ownerId_exerciseName_repRange: { ownerId, exerciseName, repRange: rr },
      },
      create: {
        ownerId,
        exerciseId,
        exerciseName,
        repRange: rr,
        weight: maxWeightSet.weightKg,
        date: new Date(),
        e1rm: bestE1rm,
      },
      update: {
        weight: maxWeightSet.weightKg,
        e1rm: bestE1rm,
        date: new Date(),
      },
    });
  }

  const progression = await prisma.exerciseProgression.upsert({
    where: { ownerId_exerciseName: { ownerId, exerciseName } },
    create: { ownerId, exerciseId, exerciseName },
    update: {},
  });

  const maxWorkingWeight = Math.max(...ws.map((s) => s.weightKg));
  const data: Record<string, unknown> = {};
  let effectiveTargetWeight = progression.targetWeight;

  if (effectiveTargetWeight == null) {
    effectiveTargetWeight = maxWorkingWeight;
    data.targetWeight = maxWorkingWeight;
  }
  if (
    progressingTargetsComplete(progression.targetSets, progression.targetReps, ws) &&
    effectiveTargetWeight != null &&
    maxWorkingWeight >= effectiveTargetWeight
  ) {
    data.targetWeight = effectiveTargetWeight + progression.incrementKg;
    data.lastProgressed = new Date();
  }

  if (Object.keys(data).length > 0) {
    await prisma.exerciseProgression.update({ where: { id: progression.id }, data });
  }
}

export async function computeAnalytics(ownerId: string) {
  const totalWorkouts = await prisma.workout.count({ where: { ownerId } });

  const now = new Date();
  const mondayOffset = (now.getDay() + 6) % 7;
  const monday = new Date(now);
  monday.setHours(0, 0, 0, 0);
  monday.setDate(now.getDate() - mondayOffset);
  const thisWeekWorkouts = await prisma.workout.count({
    where: { ownerId, date: { gte: monday } },
  });

  const sets = await prisma.workoutSet.findMany({
    where: { ownerId, setType: "working" },
    include: { workout: { select: { date: true } } },
  });

  const exercises = await prisma.exercise.findMany({
    where: { OR: [{ ownerId: null }, { ownerId }] },
    select: { name: true, muscleGroup: true },
  });
  const muscleMap = new Map(exercises.map((e) => [e.name, e.muscleGroup]));

  const volumeByDate = new Map<string, number>();
  const volumeByMuscle = new Map<string, number>();
  for (const s of sets) {
    const dateKey = s.workout.date.toISOString().slice(0, 10);
    const vol = s.weightKg * s.reps;
    volumeByDate.set(dateKey, (volumeByDate.get(dateKey) ?? 0) + vol);

    const muscle = muscleMap.get(s.exercise) ?? "Other";
    volumeByMuscle.set(muscle, (volumeByMuscle.get(muscle) ?? 0) + vol);
  }

  const volumeTrend = [...volumeByDate.entries()]
    .map(([date, kg]) => ({ date, kg: Number(kg.toFixed(1)) }))
    .sort((a, b) => a.date.localeCompare(b.date));

  const muscleVolume = [...volumeByMuscle.entries()]
    .map(([muscle, kg]) => ({ muscle, kg: Number(kg.toFixed(1)) }))
    .sort((a, b) => b.kg - a.kg);

  const prs = await prisma.personalRecord.findMany({
    where: { ownerId },
    orderBy: { date: "desc" },
    take: 20,
  });

  return { totalWorkouts, thisWeekWorkouts, volumeTrend, muscleVolume, prs };
}

progressionRouter.get("/", async (req: AuthedRequest, res) => {
  const rows = await prisma.exerciseProgression.findMany({
    where: { ownerId: req.userId },
    orderBy: { updatedAt: "desc" },
  });
  res.json(rows);
});

progressionRouter.put("/:exerciseName", async (req: AuthedRequest, res) => {
  const exerciseName = req.params.exerciseName.trim();
  if (!exerciseName) {
    return res.status(400).json({ error: "exerciseName required" });
  }

  const body: ProgressionUpdateInput = req.body ?? {};
  const data: Record<string, unknown> = {};

  if (body.targetSets !== undefined) {
    if (
      typeof body.targetSets !== "number" ||
      !Number.isInteger(body.targetSets) ||
      body.targetSets < 1
    ) {
      return res.status(400).json({ error: "invalid targetSets" });
    }
    data.targetSets = body.targetSets;
  }
  if (body.targetReps !== undefined) {
    if (
      typeof body.targetReps !== "number" ||
      !Number.isInteger(body.targetReps) ||
      body.targetReps < 1
    ) {
      return res.status(400).json({ error: "invalid targetReps" });
    }
    data.targetReps = body.targetReps;
  }
  if (body.targetWeight !== undefined && body.targetWeight !== null) {
    if (typeof body.targetWeight !== "number" || !Number.isFinite(body.targetWeight) || body.targetWeight <= 0) {
      return res.status(400).json({ error: "invalid targetWeight" });
    }
    data.targetWeight = body.targetWeight;
  }
  if (body.incrementKg !== undefined) {
    if (typeof body.incrementKg !== "number" || !Number.isFinite(body.incrementKg) || body.incrementKg <= 0) {
      return res.status(400).json({ error: "invalid incrementKg" });
    }
    data.incrementKg = body.incrementKg;
  }

  if (Object.keys(data).length === 0) {
    return res.status(400).json({ error: "no valid fields to update" });
  }

  const row = await prisma.exerciseProgression.upsert({
    where: { ownerId_exerciseName: { ownerId: req.userId!, exerciseName } },
    create: {
      ownerId: req.userId!,
      exerciseName,
      ...data,
    } as never,
    update: data as never,
  });
  res.json(row);
});
