import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { ExerciseRecord } from "shared";

export const exercisesRouter = Router();
exercisesRouter.use(requireAuth);

export const EXERCISE_MUSCLE_GROUPS = ["Chest", "Back", "Shoulders", "Arms", "Legs", "Core", "Glutes", "Other"];
export const EXERCISE_EQUIPMENT = ["Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Kettlebell", "Band", "Other"];

exercisesRouter.get("/", async (req: AuthedRequest, res) => {
  const q = typeof req.query.q === "string" ? req.query.q.trim() : "";
  const exercises = await prisma.exercise.findMany({
    where: {
      OR: [{ ownerId: null }, { ownerId: req.userId }],
      ...(q ? { name: { contains: q, mode: "insensitive" as const } } : {}),
    },
    select: { id: true, name: true, muscleGroup: true, equipment: true, isCompound: true, ownerId: true },
    orderBy: { name: "asc" },
  });
  const records: ExerciseRecord[] = exercises.map((e) => ({
    id: e.id,
    name: e.name,
    muscleGroup: e.muscleGroup,
    equipment: e.equipment,
    isCompound: e.isCompound,
    isCustom: e.ownerId === req.userId,
  }));
  res.json(records);
});

exercisesRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, muscleGroup, equipment, isCompound } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }
  if (muscleGroup !== undefined && !EXERCISE_MUSCLE_GROUPS.includes(muscleGroup as string)) {
    return res.status(400).json({ error: "invalid muscleGroup" });
  }
  if (equipment !== undefined && !EXERCISE_EQUIPMENT.includes(equipment as string)) {
    return res.status(400).json({ error: "invalid equipment" });
  }
  let created;
  try {
    created = await prisma.exercise.create({
      data: {
        ownerId: req.userId!,
        name: name.trim(),
        muscleGroup: typeof muscleGroup === "string" ? muscleGroup : "Other",
        equipment: typeof equipment === "string" ? equipment : "Bodyweight",
        isCompound: typeof isCompound === "boolean" ? isCompound : true,
      },
    });
  } catch (e) {
    if ((e as { code?: string }).code === "P2002") {
      return res.status(409).json({ error: "exercise already exists" });
    }
    throw e;
  }
  const record: ExerciseRecord = {
    id: created.id,
    name: created.name,
    muscleGroup: created.muscleGroup,
    equipment: created.equipment,
    isCompound: created.isCompound,
    isCustom: true,
  };
  res.status(201).json(record);
});
