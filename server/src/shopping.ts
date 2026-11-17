import { Router } from "express";
import type { Prisma, ShoppingItem, PantryItem, MealPlanEntry } from "@prisma/client";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { RecipeIngredient, RecipeMeal, ShoppingItemView, PantryItemView, MealPlanEntryView } from "shared";

export const shoppingRouter = Router();
export const mealPlanRouter = Router();
shoppingRouter.use(requireAuth);
mealPlanRouter.use(requireAuth);

const SLOTS: RecipeMeal[] = ["breakfast", "lunch", "dinner", "snack"];
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const norm = (s: string) => s.trim().toLowerCase();
const round2 = (n: number) => Math.round(n * 100) / 100;
const visibleTo = (userId: string): Prisma.RecipeWhereInput => ({ OR: [{ ownerId: null }, { ownerId: userId }] });

function shoppingView(i: ShoppingItem): ShoppingItemView {
  return {
    id: i.id, name: i.name, amount: i.amount, unit: i.unit, checked: i.checked,
    recipeId: i.recipeId, createdAt: i.createdAt.toISOString(), updatedAt: i.updatedAt.toISOString(),
  };
}
function pantryView(i: PantryItem): PantryItemView {
  return {
    id: i.id, name: i.name, amount: i.amount, unit: i.unit,
    createdAt: i.createdAt.toISOString(), updatedAt: i.updatedAt.toISOString(),
  };
}
function planView(e: MealPlanEntry & { recipe: { title: string } }): MealPlanEntryView {
  return {
    id: e.id, date: e.date, slot: e.slot as RecipeMeal, recipeId: e.recipeId, recipeTitle: e.recipe.title,
    servings: e.servings, createdAt: e.createdAt.toISOString(), updatedAt: e.updatedAt.toISOString(),
  };
}

function parseAmount(v: unknown, fallback: number | null): number | null | "invalid" {
  if (v === undefined) return fallback;
  if (v === null) return null;
  if (typeof v !== "number" || !Number.isFinite(v) || v < 0) return "invalid";
  return round2(v);
}

interface Need { name: string; unit: string; amount: number; recipeId: string | null }

/** Add needs to the user's list, merging by name+unit (case-insensitive) into unchecked items. */
async function addNeeds(userId: string, needs: Need[], skipPantry: boolean): Promise<ShoppingItem[]> {
  // Merge duplicates inside the batch first.
  const batch = new Map<string, Need>();
  for (const n of needs) {
    const k = `${norm(n.name)}|${norm(n.unit)}`;
    const ex = batch.get(k);
    if (ex) ex.amount += n.amount;
    else batch.set(k, { ...n });
  }

  const pantry = skipPantry ? await prisma.pantryItem.findMany({ where: { ownerId: userId } }) : [];
  const touched: ShoppingItem[] = [];
  await prisma.$transaction(async (tx) => {
    const existing = await tx.shoppingItem.findMany({ where: { ownerId: userId, checked: false } });
    for (const need of batch.values()) {
      let amount = need.amount;
      if (skipPantry) {
        const have = pantry.find((p) => norm(p.name) === norm(need.name) && (p.amount === null || norm(p.unit) === norm(need.unit)));
        if (have) {
          if (have.amount === null) continue;
          amount -= have.amount;
          if (amount <= 0) continue;
        }
      }
      amount = round2(amount);
      const match = existing.find((e) => norm(e.name) === norm(need.name) && norm(e.unit) === norm(need.unit));
      if (match) {
        const updated = await tx.shoppingItem.update({
          where: { id: match.id },
          data: { amount: round2(match.amount + amount), recipeId: match.recipeId ?? need.recipeId },
        });
        match.amount = updated.amount;
        touched.push(updated);
      } else {
        const created = await tx.shoppingItem.create({
          data: { ownerId: userId, name: need.name.trim(), unit: need.unit.trim(), amount, recipeId: need.recipeId },
        });
        existing.push(created);
        touched.push(created);
      }
    }
  });
  return touched;
}

async function needsFor(userId: string, recipeId: string, servings?: number): Promise<Need[] | null> {
  const recipe = await prisma.recipe.findFirst({ where: { id: recipeId, ...visibleTo(userId) } });
  if (!recipe) return null;
  const factor = servings && recipe.servings > 0 ? servings / recipe.servings : 1;
  const ings = JSON.parse(recipe.ingredients) as RecipeIngredient[];
  return ings.map((i) => ({ name: i.name, unit: i.unit ?? "", amount: i.amount * factor, recipeId: recipe.id }));
}

function parseServings(v: unknown): number | undefined | "invalid" {
  if (v === undefined || v === null) return undefined;
  if (typeof v !== "number" || !Number.isInteger(v) || v < 1 || v > 100) return "invalid";
  return v;
}

// ---- Shopping list ----

shoppingRouter.get("/", async (req: AuthedRequest, res) => {
  const items = await prisma.shoppingItem.findMany({
    where: { ownerId: req.userId! },
    orderBy: [{ checked: "asc" }, { name: "asc" }],
  });
  res.json(items.map(shoppingView));
});

shoppingRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, unit } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) return res.status(400).json({ error: "name is required" });
  if (unit !== undefined && typeof unit !== "string") return res.status(400).json({ error: "unit must be a string" });
  const amount = parseAmount(req.body?.amount, 1);
  if (amount === "invalid" || amount === null) return res.status(400).json({ error: "invalid amount" });
  const [item] = await addNeeds(req.userId!, [{ name, unit: unit ?? "", amount, recipeId: null }], false);
  res.status(201).json(shoppingView(item));
});

// POST /api/shopping/recipe/:recipeId — add all ingredients, scaled by servings
shoppingRouter.post("/recipe/:recipeId", async (req: AuthedRequest, res) => {
  const servings = parseServings(req.body?.servings);
  if (servings === "invalid") return res.status(400).json({ error: "servings must be an integer 1-100" });
  const needs = await needsFor(req.userId!, req.params.recipeId, servings);
  if (!needs) return res.status(404).json({ error: "recipe not found" });
  await addNeeds(req.userId!, needs, req.body?.skipPantry === true);
  const items = await prisma.shoppingItem.findMany({
    where: { ownerId: req.userId! },
    orderBy: [{ checked: "asc" }, { name: "asc" }],
  });
  res.status(201).json(items.map(shoppingView));
});

// DELETE /api/shopping/checked — clear checked items
shoppingRouter.delete("/checked", async (req: AuthedRequest, res) => {
  const r = await prisma.shoppingItem.deleteMany({ where: { ownerId: req.userId!, checked: true } });
  res.json({ deleted: r.count });
});

// ---- Pantry (static segment before /:id) ----

shoppingRouter.get("/pantry", async (req: AuthedRequest, res) => {
  const items = await prisma.pantryItem.findMany({ where: { ownerId: req.userId! }, orderBy: { name: "asc" } });
  res.json(items.map(pantryView));
});

shoppingRouter.post("/pantry", async (req: AuthedRequest, res) => {
  const { name, unit } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) return res.status(400).json({ error: "name is required" });
  if (unit !== undefined && typeof unit !== "string") return res.status(400).json({ error: "unit must be a string" });
  const amount = parseAmount(req.body?.amount, null);
  if (amount === "invalid") return res.status(400).json({ error: "invalid amount" });
  const item = await prisma.pantryItem.create({
    data: { ownerId: req.userId!, name: name.trim(), unit: (unit ?? "").trim(), amount },
  });
  res.status(201).json(pantryView(item));
});

shoppingRouter.patch("/pantry/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.pantryItem.findFirst({ where: { id: req.params.id, ownerId: req.userId! } });
  if (!existing) return res.status(404).json({ error: "not found" });
  const { name, unit } = req.body ?? {};
  if (name !== undefined && (typeof name !== "string" || !name.trim())) return res.status(400).json({ error: "invalid name" });
  if (unit !== undefined && typeof unit !== "string") return res.status(400).json({ error: "invalid unit" });
  const amount = parseAmount(req.body?.amount, existing.amount);
  if (amount === "invalid") return res.status(400).json({ error: "invalid amount" });
  const item = await prisma.pantryItem.update({
    where: { id: existing.id },
    data: { name: name?.trim(), unit: unit?.trim(), amount },
  });
  res.json(pantryView(item));
});

shoppingRouter.delete("/pantry/:id", async (req: AuthedRequest, res) => {
  const r = await prisma.pantryItem.deleteMany({ where: { id: req.params.id, ownerId: req.userId! } });
  if (r.count === 0) return res.status(404).json({ error: "not found" });
  res.status(204).end();
});

// ---- Single shopping item ----

shoppingRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.shoppingItem.findFirst({ where: { id: req.params.id, ownerId: req.userId! } });
  if (!existing) return res.status(404).json({ error: "not found" });
  const { name, unit, checked } = req.body ?? {};
  if (name !== undefined && (typeof name !== "string" || !name.trim())) return res.status(400).json({ error: "invalid name" });
  if (unit !== undefined && typeof unit !== "string") return res.status(400).json({ error: "invalid unit" });
  if (checked !== undefined && typeof checked !== "boolean") return res.status(400).json({ error: "checked must be boolean" });
  const amount = parseAmount(req.body?.amount, existing.amount);
  if (amount === "invalid" || amount === null) return res.status(400).json({ error: "invalid amount" });
  const item = await prisma.shoppingItem.update({
    where: { id: existing.id },
    data: { name: name?.trim(), unit: unit?.trim(), checked, amount },
  });
  res.json(shoppingView(item));
});

shoppingRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const r = await prisma.shoppingItem.deleteMany({ where: { id: req.params.id, ownerId: req.userId! } });
  if (r.count === 0) return res.status(404).json({ error: "not found" });
  res.status(204).end();
});

// ---- Meal plan ----

// GET /api/mealplan?from=YYYY-MM-DD&to=YYYY-MM-DD (defaults: the next 7 days from today)
mealPlanRouter.get("/", async (req: AuthedRequest, res) => {
  const from = typeof req.query.from === "string" ? req.query.from : new Date().toISOString().slice(0, 10);
  const to = typeof req.query.to === "string"
    ? req.query.to
    : new Date(new Date(`${from}T00:00:00Z`).getTime() + 6 * 86400000).toISOString().slice(0, 10);
  if (!DATE_RE.test(from) || !DATE_RE.test(to)) return res.status(400).json({ error: "from/to must be YYYY-MM-DD" });
  const entries = await prisma.mealPlanEntry.findMany({
    where: { ownerId: req.userId!, date: { gte: from, lte: to } },
    include: { recipe: { select: { title: true } } },
    orderBy: [{ date: "asc" }, { createdAt: "asc" }],
  });
  const slotOrder = (s: string) => SLOTS.indexOf(s as RecipeMeal);
  entries.sort((a, b) => a.date.localeCompare(b.date) || slotOrder(a.slot) - slotOrder(b.slot));
  res.json(entries.map(planView));
});

// POST /api/mealplan/shopping — add every planned recipe in a range to the shopping list
mealPlanRouter.post("/shopping", async (req: AuthedRequest, res) => {
  const { from, to } = req.body ?? {};
  if (typeof from !== "string" || typeof to !== "string" || !DATE_RE.test(from) || !DATE_RE.test(to)) {
    return res.status(400).json({ error: "from/to must be YYYY-MM-DD" });
  }
  const entries = await prisma.mealPlanEntry.findMany({
    where: { ownerId: req.userId!, date: { gte: from, lte: to } },
  });
  const needs: Need[] = [];
  for (const e of entries) {
    const n = await needsFor(req.userId!, e.recipeId, e.servings ?? undefined);
    if (n) needs.push(...n);
  }
  await addNeeds(req.userId!, needs, req.body?.skipPantry === true);
  const items = await prisma.shoppingItem.findMany({
    where: { ownerId: req.userId! },
    orderBy: [{ checked: "asc" }, { name: "asc" }],
  });
  res.status(201).json(items.map(shoppingView));
});

// PUT /api/mealplan/:date/:slot { recipeId, servings? } — set (replace) the entry
mealPlanRouter.put("/:date/:slot", async (req: AuthedRequest, res) => {
  const { date, slot } = req.params;
  if (!DATE_RE.test(date) || Number.isNaN(Date.parse(`${date}T00:00:00Z`))) return res.status(400).json({ error: "date must be YYYY-MM-DD" });
  if (!SLOTS.includes(slot as RecipeMeal)) return res.status(400).json({ error: "invalid slot" });
  const { recipeId } = req.body ?? {};
  if (typeof recipeId !== "string" || !recipeId) return res.status(400).json({ error: "recipeId is required" });
  const servings = parseServings(req.body?.servings);
  if (servings === "invalid") return res.status(400).json({ error: "servings must be an integer 1-100" });
  const recipe = await prisma.recipe.findFirst({ where: { id: recipeId, ...visibleTo(req.userId!) } });
  if (!recipe) return res.status(404).json({ error: "recipe not found" });
  const entry = await prisma.mealPlanEntry.upsert({
    where: { ownerId_date_slot: { ownerId: req.userId!, date, slot } },
    create: { ownerId: req.userId!, date, slot, recipeId, servings: servings ?? null },
    update: { recipeId, servings: servings ?? null },
    include: { recipe: { select: { title: true } } },
  });
  res.json(planView(entry));
});

mealPlanRouter.delete("/:date/:slot", async (req: AuthedRequest, res) => {
  const r = await prisma.mealPlanEntry.deleteMany({
    where: { ownerId: req.userId!, date: req.params.date, slot: req.params.slot },
  });
  if (r.count === 0) return res.status(404).json({ error: "not found" });
  res.status(204).end();
});
