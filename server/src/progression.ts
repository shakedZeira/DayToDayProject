import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { DeloadSuggestion, ProgressionUpdateInput } from "shared";

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

export interface SessionPoint {
  date: string;
  bestE1rm: number;
  topWeightKg: number;
}

export const DELOAD_DROP_RATIO = 0.95;
export const DELOAD_FACTOR = 0.9;
export const STALL_SESSIONS = 3;

export function roundToPlate(kg: number, step = 2.5): number {
  return Math.round(kg / step) * step;
}

/**
 * Decides whether an exercise needs a deload from its sessions (oldest first).
 * - e1rm_drop: the latest session's best e1RM is >5% below the best of the 3 sessions before it.
 * - stalled: none of the last 3 sessions beat the best e1RM recorded before them (needs 4+ sessions).
 */
export function detectDeload(
  sessions: SessionPoint[]
): { reason: "e1rm_drop" | "stalled"; detail: string } | null {
  const n = sessions.length;
  if (n < 3) return null;
  const latest = sessions[n - 1];
  const prevBest = Math.max(...sessions.slice(Math.max(0, n - 4), n - 1).map((s) => s.bestE1rm));
  if (latest.bestE1rm < prevBest * DELOAD_DROP_RATIO) {
    const pct = Math.round((1 - latest.bestE1rm / prevBest) * 100);
    return { reason: "e1rm_drop", detail: `Estimated 1RM fell ${pct}% versus your recent best.` };
  }
  if (n >= STALL_SESSIONS + 1) {
    const ref = Math.max(...sessions.slice(0, n - STALL_SESSIONS).map((s) => s.bestE1rm));
    if (sessions.slice(n - STALL_SESSIONS).every((s) => s.bestE1rm <= ref)) {
      return { reason: "stalled", detail: `No estimated 1RM improvement in the last ${STALL_SESSIONS} sessions.` };
    }
  }
  return null;
}

export function deloadSuggestions(
  byExercise: Map<string, SessionPoint[]>
): DeloadSuggestion[] {
  const out: DeloadSuggestion[] = [];
  for (const [exerciseName, sessions] of byExercise) {
    const d = detectDeload(sessions);
    if (!d) continue;
    const current = sessions[sessions.length - 1].topWeightKg;
    out.push({
      exerciseName,
      reason: d.reason,
      detail: d.detail,
      currentWeightKg: current,
      suggestedWeightKg: Math.max(0, roundToPlate(current * DELOAD_FACTOR)),
    });
  }
  return out.sort((a, b) => a.exerciseName.localeCompare(b.exerciseName));
}

/** Monday (UTC) of the week containing d, as YYYY-MM-DD. */
export function weekStartOf(d: Date): string {
  const x = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  x.setUTCDate(x.getUTCDate() - ((x.getUTCDay() + 6) % 7));
  return x.toISOString().slice(0, 10);
}

/** Working sets and volume per muscle for each of the last `weeks` weeks (oldest first). */
export function weeklyMuscleVolume(
  sets: { date: Date; exercise: string; weightKg: number; reps: number }[],
  muscleMap: Map<string, string>,
  now: Date,
  weeks = 8
): { weekStart: string; muscle: string; sets: number; kg: number }[] {
  const current = new Date(`${weekStartOf(now)}T00:00:00Z`);
  const valid = new Set<string>();
  for (let i = 0; i < weeks; i++) {
    valid.add(new Date(current.getTime() - i * 7 * 86400000).toISOString().slice(0, 10));
  }
  const acc = new Map<string, { weekStart: string; muscle: string; sets: number; kg: number }>();
  for (const s of sets) {
    const weekStart = weekStartOf(s.date);
    if (!valid.has(weekStart)) continue;
    const muscle = muscleMap.get(s.exercise) ?? "Other";
    const key = `${weekStart}|${muscle}`;
    const row = acc.get(key) ?? { weekStart, muscle, sets: 0, kg: 0 };
    row.sets += 1;
    row.kg += s.weightKg * s.reps;
    acc.set(key, row);
  }
  return [...acc.values()]
    .map((r) => ({ ...r, kg: Number(r.kg.toFixed(1)) }))
    .sort((a, b) => a.weekStart.localeCompare(b.weekStart) || a.muscle.localeCompare(b.muscle));
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

  const weekly = weeklyMuscleVolume(
    sets.map((s) => ({ date: s.workout.date, exercise: s.exercise, weightKg: s.weightKg, reps: s.reps })),
    muscleMap,
    now
  );

  return { totalWorkouts, thisWeekWorkouts, volumeTrend, muscleVolume, weeklyMuscleVolume: weekly, prs };
}

progressionRouter.get("/", async (req: AuthedRequest, res) => {
  const rows = await prisma.exerciseProgression.findMany({
    where: { ownerId: req.userId },
    orderBy: { updatedAt: "desc" },
  });
  res.json(rows);
});

// GET /api/progression/deload — exercises whose recent sessions suggest a deload.
progressionRouter.get("/deload", async (req: AuthedRequest, res) => {
  const sets = await prisma.workoutSet.findMany({
    where: { ownerId: req.userId, setType: "working" },
    include: { workout: { select: { id: true, date: true } } },
  });
  const sessions = new Map<string, Map<string, SessionPoint>>();
  for (const s of sets) {
    const perEx = sessions.get(s.exercise) ?? new Map<string, SessionPoint>();
    const p = perEx.get(s.workout.id) ?? { date: s.workout.date.toISOString(), bestE1rm: 0, topWeightKg: 0 };
    p.bestE1rm = Math.max(p.bestE1rm, e1rm(s.weightKg, s.reps));
    p.topWeightKg = Math.max(p.topWeightKg, s.weightKg);
    perEx.set(s.workout.id, p);
    sessions.set(s.exercise, perEx);
  }
  const byExercise = new Map<string, SessionPoint[]>();
  for (const [name, m] of sessions) {
    byExercise.set(name, [...m.values()].sort((a, b) => a.date.localeCompare(b.date)));
  }
  res.json(deloadSuggestions(byExercise));
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
