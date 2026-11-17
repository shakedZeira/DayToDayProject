import { Router } from "express";
import { prisma } from "./db";
import { isValidBarcode, lookupOpenFoodFacts } from "./barcode";
import { requireAuth, type AuthedRequest } from "./session";

export const foodsRouter = Router();
foodsRouter.use(requireAuth);

const FOOD_SELECT = {
  id: true, name: true, nameHe: true, caloriesPer100: true, servingUnit: true,
  proteinPer100: true, carbsPer100: true, fatPer100: true, barcode: true, ownerId: true,
} as const;

type FoodRow = { ownerId: string | null } & Record<string, unknown>;
function present({ ownerId, ...rest }: FoodRow, userId: string | undefined) {
  return { ...rest, custom: ownerId !== null && ownerId === userId };
}
function visible(userId: string | undefined) {
  return { recipeId: null, OR: [{ ownerId: null }, { ownerId: userId }] };
}
function macro(v: unknown): number | null {
  if (v === undefined || v === null) return 0;
  return typeof v === "number" && Number.isFinite(v) && v >= 0 ? v : null;
}

// GET /api/foods/barcode/:code — Open Food Facts lookup (or the user's own saved food with that barcode).
foodsRouter.get("/barcode/:code", async (req: AuthedRequest, res) => {
  const code = req.params.code.trim();
  if (!isValidBarcode(code)) return res.status(400).json({ error: "barcode must be 6-14 digits" });
  const mine = await prisma.food.findFirst({ where: { ownerId: req.userId, barcode: code } });
  if (mine) {
    return res.json({
      barcode: code,
      source: "mine",
      foodId: mine.id,
      food: {
        name: mine.name, nameHe: mine.nameHe, caloriesPer100: mine.caloriesPer100, servingUnit: mine.servingUnit,
        proteinPer100: mine.proteinPer100, carbsPer100: mine.carbsPer100, fatPer100: mine.fatPer100, barcode: code,
      },
    });
  }
  let food;
  try {
    food = await lookupOpenFoodFacts(code);
  } catch {
    return res.status(502).json({ error: "barcode lookup unavailable" });
  }
  if (!food) return res.status(404).json({ error: "product not found" });
  res.json({ barcode: code, source: "openfoodfacts", food });
});

foodsRouter.get("/", async (req: AuthedRequest, res) => {
  const q = typeof req.query.q === "string" ? req.query.q.trim().toLowerCase() : "";
  const foods = await prisma.food.findMany({
    where: {
      ...visible(req.userId),
      ...(q
        ? { OR: [{ name: { contains: q, mode: "insensitive" as const } }, { nameHe: { contains: q, mode: "insensitive" as const } }] }
        : {}),
    },
    select: FOOD_SELECT,
    orderBy: { name: "asc" },
  });
  res.json(foods.map((f) => present(f, req.userId)));
});

foodsRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, caloriesPer100, servingUnit, nameHe, barcode } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }
  if (typeof caloriesPer100 !== "number" || !Number.isFinite(caloriesPer100) || caloriesPer100 < 0) {
    return res.status(400).json({ error: "caloriesPer100 must be a number >= 0" });
  }
  const protein = macro(req.body?.proteinPer100);
  const carbs = macro(req.body?.carbsPer100);
  const fat = macro(req.body?.fatPer100);
  if (protein === null || carbs === null || fat === null) {
    return res.status(400).json({ error: "proteinPer100, carbsPer100 and fatPer100 must be numbers >= 0" });
  }
  if (barcode !== undefined && barcode !== null && (typeof barcode !== "string" || !isValidBarcode(barcode))) {
    return res.status(400).json({ error: "barcode must be 6-14 digits" });
  }
  if (await prisma.food.findUnique({ where: { name: name.trim() } })) {
    return res.status(409).json({ error: "a food with this name already exists" });
  }
  const created = await prisma.food.create({
    data: {
      ownerId: req.userId!,
      proteinPer100: protein,
      carbsPer100: carbs,
      fatPer100: fat,
      barcode: typeof barcode === "string" ? barcode : null,
      name: name.trim(),
      nameHe: typeof nameHe === "string" && nameHe.trim() ? nameHe.trim() : null,
      caloriesPer100,
      servingUnit: typeof servingUnit === "string" && servingUnit.trim() ? servingUnit.trim() : "g",
    },
  });
  res.status(201).json(present(created, req.userId));
});

foodsRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.food.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.food.delete({ where: { id: existing.id } });
  res.status(204).end();
});

foodsRouter.post("/search", async (req: AuthedRequest, res) => {
  const q = typeof req.body?.q === "string" ? req.body.q.trim().toLowerCase() : "";
  if (!q) {
    return res.status(400).json({ error: "q required" });
  }
  const foods = await prisma.food.findMany({
    where: {
      AND: [
        visible(req.userId),
        { OR: [{ name: { contains: q, mode: "insensitive" as const } }, { nameHe: { contains: q, mode: "insensitive" as const } }] },
      ],
    },
    select: FOOD_SELECT,
    orderBy: { name: "asc" },
  });
  res.json(foods.map((f) => present(f, req.userId)));
});

foodsRouter.post("/meals", async (req: AuthedRequest, res) => {
  const { foodId, grams, date } = req.body ?? {};
  if (typeof foodId !== "string" || !foodId) {
    return res.status(400).json({ error: "foodId required" });
  }
  if (typeof grams !== "number" || !Number.isFinite(grams) || grams <= 0) {
    return res.status(400).json({ error: "grams must be a number > 0" });
  }
  const food = await prisma.food.findFirst({
    where: { id: foodId, OR: [{ ownerId: null }, { ownerId: req.userId }] },
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