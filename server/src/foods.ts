import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const foodsRouter = Router();
foodsRouter.use(requireAuth);

foodsRouter.get("/", async (req: AuthedRequest, res) => {
  const q = typeof req.query.q === "string" ? req.query.q.trim().toLowerCase() : "";
  const foods = await prisma.food.findMany({
    where: {
      ...(q
        ? { OR: [{ name: { contains: q, mode: "insensitive" as const } }, { nameHe: { contains: q, mode: "insensitive" as const } }] }
        : {}),
    },
    select: { id: true, name: true, nameHe: true, caloriesPer100: true, servingUnit: true },
    orderBy: { name: "asc" },
  });
  res.json(foods);
});

foodsRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, caloriesPer100, servingUnit, nameHe } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }
  if (typeof caloriesPer100 !== "number" || !Number.isFinite(caloriesPer100) || caloriesPer100 < 0) {
    return res.status(400).json({ error: "caloriesPer100 must be a number >= 0" });
  }
  const food = await prisma.food.create({
    data: {
      name: name.trim(),
      nameHe: typeof nameHe === "string" && nameHe.trim() ? nameHe.trim() : null,
      caloriesPer100,
      servingUnit: typeof servingUnit === "string" && servingUnit.trim() ? servingUnit.trim() : "g",
    },
  });
  res.status(201).json(food);
});

foodsRouter.post("/search", async (req: AuthedRequest, res) => {
  const q = typeof req.body?.q === "string" ? req.body.q.trim().toLowerCase() : "";
  if (!q) {
    return res.status(400).json({ error: "q required" });
  }
  const foods = await prisma.food.findMany({
    where: {
      OR: [{ name: { contains: q, mode: "insensitive" as const } }, { nameHe: { contains: q, mode: "insensitive" as const } }],
    },
    select: { id: true, name: true, nameHe: true, caloriesPer100: true, servingUnit: true },
    orderBy: { name: "asc" },
  });
  res.json(foods);
});

foodsRouter.post("/meals", async (req: AuthedRequest, res) => {
  const { foodId, grams, date } = req.body ?? {};
  if (typeof foodId !== "string" || !foodId) {
    return res.status(400).json({ error: "foodId required" });
  }
  if (typeof grams !== "number" || !Number.isFinite(grams) || grams <= 0) {
    return res.status(400).json({ error: "grams must be a number > 0" });
  }
  const food = await prisma.food.findUnique({
    where: { id: foodId },
  });
  if (!food) return res.status(404).json({ error: "not found" });

  const meal = await prisma.mealLog.create({
    data: {
      ownerId: req.userId!,
      foodId: food.id,
      grams,
      date: date ? new Date(date) : new Date(),
    },
  });
  res.status(201).json(meal);
});

foodsRouter.delete("/meals/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.mealLog.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.mealLog.delete({ where: { id: existing.id } });
  res.status(204).end();
});