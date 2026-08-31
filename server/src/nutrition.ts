import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const nutritionRouter = Router();
nutritionRouter.use(requireAuth);

function startOfLocalDay(date: string): { start: Date; end: Date } {
  const start = new Date(`${date}T00:00:00`);
  const end = new Date(start.getTime() + 24 * 60 * 60 * 1000);
  return { start, end };
}

function resolveDate(date: unknown): string {
  return typeof date === "string" && date ? date : new Date().toISOString().slice(0, 10);
}

nutritionRouter.get("/summary", async (req: AuthedRequest, res) => {
  const date = resolveDate(req.query.date);
  const { start, end } = startOfLocalDay(date);

  const dailyTarget = await prisma.dailyTarget.findUnique({
    where: { ownerId: req.userId! },
  });
  const target = dailyTarget?.calories ?? 2000;

  const meals = await prisma.mealLog.findMany({
    where: { ownerId: req.userId, date: { gte: start, lt: end } },
    include: { food: { select: { id: true, name: true, caloriesPer100: true } } },
    orderBy: { date: "asc" },
  });

  const consumed = Math.round(
    meals.reduce((sum, m) => sum + m.food.caloriesPer100 * (m.grams / 100), 0)
  );

  res.json({
    date,
    target,
    consumed,
    remaining: target - consumed,
    meals: meals.map((m) => ({
      id: m.id,
      foodName: m.food.name,
      grams: m.grams,
      calories: Math.round(m.food.caloriesPer100 * (m.grams / 100)),
    })),
  });
});

nutritionRouter.get("/detail", async (req: AuthedRequest, res) => {
  const date = resolveDate(req.query.date);
  const { start, end } = startOfLocalDay(date);

  const meals = await prisma.mealLog.findMany({
    where: { ownerId: req.userId, date: { gte: start, lt: end } },
    include: { food: { select: { id: true, name: true, caloriesPer100: true } } },
    orderBy: { date: "asc" },
  });

  res.json({
    date,
    meals: meals.map((m) => ({
      id: m.id,
      foodId: m.foodId,
      foodName: m.food.name,
      grams: m.grams,
      calories: Math.round(m.food.caloriesPer100 * (m.grams / 100)),
    })),
  });
});

nutritionRouter.put("/target", async (req: AuthedRequest, res) => {
  const { calories } = req.body ?? {};
  if (typeof calories !== "number" || !Number.isFinite(calories) || calories <= 0) {
    return res.status(400).json({ error: "calories must be a finite number > 0" });
  }
  const row = await prisma.dailyTarget.upsert({
    where: { ownerId: req.userId! },
    create: { ownerId: req.userId!, calories },
    update: { calories },
  });
  res.json(row);
});