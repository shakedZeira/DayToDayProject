import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { RoutineView, RoutineDayView, RoutineExerciseInput } from "shared";

export const routinesRouter = Router();
routinesRouter.use(requireAuth);

async function isUsableExercise(exerciseId: string, userId: string): Promise<boolean> {
  const ex = await prisma.exercise.findFirst({
    where: { id: exerciseId, OR: [{ ownerId: null }, { ownerId: userId }] },
  });
  return !!ex;
}

function mapToView(routine: any): RoutineView {
  return {
    id: routine.id,
    name: routine.name,
    description: routine.description,
    days: routine.days.map((d: any): RoutineDayView => ({
      id: d.id,
      name: d.name,
      order: d.order,
      exercises: d.exercises.map((re: any) => ({
        id: re.id,
        order: re.order,
        targetSets: re.targetSets,
        targetReps: re.targetReps,
        targetWeight: re.targetWeight,
        notes: re.notes,
        exerciseId: re.exerciseId,
        exercise: {
          id: re.exercise.id,
          name: re.exercise.name,
          muscleGroup: re.exercise.muscleGroup,
          equipment: re.exercise.equipment,
          isCompound: re.exercise.isCompound,
          isCustom: re.exercise.ownerId !== null,
        },
      })),
    })),
  };
}

routinesRouter.get("/", async (req: AuthedRequest, res) => {
  const routines = await prisma.routine.findMany({
    where: { ownerId: req.userId },
    orderBy: { createdAt: "desc" },
    include: {
      days: {
        orderBy: { order: "asc" },
        include: {
          exercises: {
            orderBy: { order: "asc" },
            include: { exercise: true },
          },
        },
      },
    },
  });
  res.json(routines.map(mapToView));
});

routinesRouter.get("/:id", async (req: AuthedRequest, res) => {
  const routine = await prisma.routine.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
    include: {
      days: {
        orderBy: { order: "asc" },
        include: {
          exercises: {
            orderBy: { order: "asc" },
            include: { exercise: true },
          },
        },
      },
    },
  });
  if (!routine) return res.status(404).json({ error: "not found" });
  res.json(mapToView(routine));
});

routinesRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, description, days } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }

  if (Array.isArray(days)) {
    for (const day of days) {
      if (typeof day.name !== "string" || !day.name.trim()) {
        return res.status(400).json({ error: "day name required" });
      }
      if (Array.isArray(day.exercises)) {
        for (const ex of day.exercises) {
          if (!Number.isInteger(ex.order) || ex.order < 0) {
            return res.status(400).json({ error: "exercise order must be non-negative integer" });
          }
          if (!Number.isInteger(ex.targetSets) || ex.targetSets < 1) {
            return res.status(400).json({ error: "targetSets must be >= 1" });
          }
          if (!Number.isInteger(ex.targetReps) || ex.targetReps < 1) {
            return res.status(400).json({ error: "targetReps must be >= 1" });
          }
          if (ex.targetWeight !== undefined && ex.targetWeight !== null && (typeof ex.targetWeight !== "number" || ex.targetWeight <= 0)) {
            return res.status(400).json({ error: "targetWeight must be > 0" });
          }
          if (typeof ex.exerciseId !== "string") {
            return res.status(400).json({ error: "exerciseId required" });
          }
          if (!(await isUsableExercise(ex.exerciseId, req.userId!))) {
            return res.status(400).json({ error: "unknown exercise" });
          }
        }
      }
    }
  }

  const routine = await prisma.routine.create({
    data: {
      ownerId: req.userId!,
      name: name.trim(),
      description: typeof description === "string" ? description : null,
      days: Array.isArray(days)
        ? {
            create: days.map((day: any, dayIdx: number) => ({
              ownerId: req.userId!,
              name: day.name.trim(),
              order: typeof day.order === "number" ? day.order : dayIdx,
              exercises: Array.isArray(day.exercises)
                ? {
                    create: day.exercises.map((ex: any) => ({
                      ownerId: req.userId!,
                      order: ex.order,
                      targetSets: ex.targetSets,
                      targetReps: ex.targetReps,
                      targetWeight: ex.targetWeight ?? null,
                      notes: typeof ex.notes === "string" ? ex.notes : null,
                      exerciseId: ex.exerciseId,
                    })),
                  }
                : undefined,
            })),
          }
        : undefined,
    },
    include: {
      days: {
        orderBy: { order: "asc" },
        include: {
          exercises: {
            orderBy: { order: "asc" },
            include: { exercise: true },
          },
        },
      },
    },
  });

  res.status(201).json(mapToView(routine));
});

routinesRouter.put("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.routine.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });

  const { name, description, days } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }

  if (Array.isArray(days)) {
    for (const day of days) {
      if (typeof day.name !== "string" || !day.name.trim()) {
        return res.status(400).json({ error: "day name required" });
      }
      if (Array.isArray(day.exercises)) {
        for (const ex of day.exercises) {
          if (!Number.isInteger(ex.order) || ex.order < 0) {
            return res.status(400).json({ error: "exercise order must be non-negative integer" });
          }
          if (!Number.isInteger(ex.targetSets) || ex.targetSets < 1) {
            return res.status(400).json({ error: "targetSets must be >= 1" });
          }
          if (!Number.isInteger(ex.targetReps) || ex.targetReps < 1) {
            return res.status(400).json({ error: "targetReps must be >= 1" });
          }
          if (ex.targetWeight !== undefined && ex.targetWeight !== null && (typeof ex.targetWeight !== "number" || ex.targetWeight <= 0)) {
            return res.status(400).json({ error: "targetWeight must be > 0" });
          }
          if (typeof ex.exerciseId !== "string") {
            return res.status(400).json({ error: "exerciseId required" });
          }
          if (!(await isUsableExercise(ex.exerciseId, req.userId!))) {
            return res.status(400).json({ error: "unknown exercise" });
          }
        }
      }
    }
  }

  // Delete existing days (cascade deletes RoutineExercises)
  await prisma.routineDay.deleteMany({ where: { routineId: existing.id } });

  // Update routine and recreate days
  const routine = await prisma.routine.update({
    where: { id: existing.id },
    data: {
      name: name.trim(),
      description: typeof description === "string" ? description : null,
      days: Array.isArray(days)
        ? {
            create: days.map((day: any, dayIdx: number) => ({
              ownerId: req.userId!,
              name: day.name.trim(),
              order: typeof day.order === "number" ? day.order : dayIdx,
              exercises: Array.isArray(day.exercises)
                ? {
                    create: day.exercises.map((ex: any) => ({
                      ownerId: req.userId!,
                      order: ex.order,
                      targetSets: ex.targetSets,
                      targetReps: ex.targetReps,
                      targetWeight: ex.targetWeight ?? null,
                      notes: typeof ex.notes === "string" ? ex.notes : null,
                      exerciseId: ex.exerciseId,
                    })),
                  }
                : undefined,
            })),
          }
        : undefined,
    },
    include: {
      days: {
        orderBy: { order: "asc" },
        include: {
          exercises: {
            orderBy: { order: "asc" },
            include: { exercise: true },
          },
        },
      },
    },
  });

  res.json(mapToView(routine));
});

routinesRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.routine.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.routine.delete({ where: { id: existing.id } });
  res.status(204).end();
});
