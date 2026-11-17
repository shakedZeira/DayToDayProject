import { Router } from "express";
import multer from "multer";
import { Prisma } from "@prisma/client";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { RecipeCreateInput, RecipeIngredient, RecipeInstructionStep, RecipeView, RecipeMeal, RecipeDifficulty } from "shared";

export const recipesRouter = Router();

const PHOTO_URL_PREFIX = "/api/recipes/uploads/";

// GET /api/recipes/uploads/:id — serve an uploaded recipe photo from Postgres.
// Deliberately registered BEFORE requireAuth: <img> tags and Coil cannot send a
// Bearer header, and the id is an unguessable cuid.
recipesRouter.get("/uploads/:id", async (req, res) => {
  const photo = await prisma.recipePhoto.findUnique({ where: { id: req.params.id } });
  if (!photo) return res.status(404).json({ error: "not found" });
  res.setHeader("Content-Type", photo.mimeType);
  res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
  res.send(Buffer.from(photo.data));
});

recipesRouter.use(requireAuth);

type Meal = RecipeMeal;
type Difficulty = RecipeDifficulty;

const VALID_MEALS: Meal[] = ["breakfast", "lunch", "dinner", "snack"];
const VALID_DIFFICULTIES: Difficulty[] = ["beginner", "intermediate", "advanced"];

// Photos are stored in Postgres (RecipePhoto) so they survive ephemeral hosting.
const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_PHOTO_BYTES },
  fileFilter: (_req, file, cb) => {
    if (file.mimetype.startsWith("image/")) {
      cb(null, true);
    } else {
      cb(new Error("only image files are allowed"));
    }
  },
});

async function deleteOwnedPhoto(imageUrl: string | null | undefined, ownerId: string) {
  if (!imageUrl || !imageUrl.startsWith(PHOTO_URL_PREFIX)) return;
  await prisma.recipePhoto.deleteMany({ where: { id: imageUrl.slice(PHOTO_URL_PREFIX.length), ownerId } });
}

function normalizeTags(tags: unknown): string | null {
  if (tags == null) return null;
  if (typeof tags === "string") return tags.trim() || null;
  if (Array.isArray(tags)) {
    const arr = tags
      .filter((t): t is string => typeof t === "string")
      .map((t) => t.trim())
      .filter(Boolean);
    return arr.length ? arr.join(",") : null;
  }
  return null;
}

type RecipeWithFavs = Prisma.RecipeGetPayload<{
  include: { favorites: { select: { ownerId: true } }; _count: { select: { favorites: true } } };
}>;

function toRecipeView(
  r: Omit<RecipeWithFavs, "favorites" | "_count"> & {
    favorites?: RecipeWithFavs["favorites"];
    _count?: { favorites: number };
  },
  userId?: string,
): RecipeView {
  return {
    id: r.id,
    title: r.title,
    description: r.description,
    meal: r.meal as Meal,
    difficulty: r.difficulty as Difficulty,
    prepTimeMin: r.prepTimeMin,
    cookTimeMin: r.cookTimeMin,
    servings: r.servings,
    calories: r.calories,
    proteinG: r.proteinG,
    carbsG: r.carbsG,
    fatG: r.fatG,
    ingredients: JSON.parse(r.ingredients) as RecipeIngredient[],
    instructions: JSON.parse(r.instructions) as RecipeInstructionStep[],
    imageUrl: r.imageUrl ?? null,
    source: r.source ?? null,
    tags: r.tags ? r.tags.split(",").filter(Boolean) : null,
    createdAt: r.createdAt.toISOString(),
    updatedAt: r.updatedAt.toISOString(),
    favoriteCount: r._count?.favorites ?? 0,
    isFavorited: userId ? (r.favorites ?? []).some((f) => f.ownerId === userId) : false,
  };
}

interface RecipeCreateBody extends Omit<RecipeCreateInput, "imageUrl"> {
  imageUrl?: string | null;
}

// GET /api/recipes — list recipes. ?meal=, ?favorites=true, ?mine=true
recipesRouter.get("/", async (req: AuthedRequest, res) => {
  const userId = req.userId;
  const meal = typeof req.query.meal === "string" ? req.query.meal : undefined;
  const favorites = req.query.favorites === "true";
  const mine = req.query.mine === "true";

  const where: Prisma.RecipeWhereInput = {};
  if (meal) where.meal = meal;
  if (favorites) {
    where.favorites = { some: { ownerId: userId! } };
  } else if (mine) {
    where.ownerId = userId!;
  } else {
    where.ownerId = null;
  }

  const recipes = await prisma.recipe.findMany({
    where,
    include: {
      favorites: userId ? { select: { ownerId: true } } : undefined,
      _count: { select: { favorites: true } },
    },
    orderBy: { createdAt: "desc" },
  });

  res.json(recipes.map((r) => toRecipeView(r, userId)));
});

// GET /api/recipes/favorites — list the authed user's favorite recipes (static segment before /:id)
recipesRouter.get("/favorites", async (req: AuthedRequest, res) => {
  const userId = req.userId!;
  const recipes = await prisma.recipe.findMany({
    where: { favorites: { some: { ownerId: userId } } },
    include: {
      favorites: { select: { ownerId: true } },
      _count: { select: { favorites: true } },
    },
    orderBy: { createdAt: "desc" },
  });
  res.json(recipes.map((r) => toRecipeView(r, userId)));
});

// GET /api/recipes/:id — single recipe with isFavorite flag
recipesRouter.get("/:id", async (req: AuthedRequest, res) => {
  const userId = req.userId;
  const r = await prisma.recipe.findUnique({
    where: { id: req.params.id },
    include: {
      favorites: userId ? { select: { ownerId: true } } : undefined,
      _count: { select: { favorites: true } },
    },
  });
  if (!r) return res.status(404).json({ error: "not found" });
  res.json(toRecipeView(r, userId));
});

// POST /api/recipes — create a recipe (ownerId = authed user)
recipesRouter.post("/", async (req: AuthedRequest, res) => {
  const body = req.body ?? {};
  const {
    title,
    description,
    meal,
    difficulty,
    prepTimeMin,
    cookTimeMin,
    servings,
    calories,
    proteinG,
    carbsG,
    fatG,
    ingredients,
    instructions,
    source,
    tags,
    imageUrl,
  } = body;

  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  if (!meal || !VALID_MEALS.includes(meal as Meal)) {
    return res.status(400).json({ error: "meal must be one of breakfast/lunch/dinner/snack" });
  }
  if (!difficulty || !VALID_DIFFICULTIES.includes(difficulty as Difficulty)) {
    return res.status(400).json({ error: "difficulty must be one of beginner/intermediate/advanced" });
  }
  for (const [key, value] of Object.entries({ prepTimeMin, cookTimeMin, servings, calories, proteinG, carbsG, fatG })) {
    if (typeof value !== "number" || !Number.isFinite(value)) {
      return res.status(400).json({ error: `${key} must be a number` });
    }
  }
  if (!Array.isArray(ingredients) || ingredients.length === 0) {
    return res.status(400).json({ error: "ingredients required" });
  }
  if (!Array.isArray(instructions) || instructions.length === 0) {
    return res.status(400).json({ error: "instructions required" });
  }

  const recipe = await prisma.recipe.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      description: typeof description === "string" ? description.trim() || null : null,
      meal: meal as Meal,
      difficulty: difficulty as Difficulty,
      prepTimeMin: prepTimeMin as number,
      cookTimeMin: cookTimeMin as number,
      servings: servings as number,
      calories: calories as number,
      proteinG: proteinG as number,
      carbsG: carbsG as number,
      fatG: fatG as number,
      ingredients: JSON.stringify(ingredients),
      instructions: JSON.stringify(instructions),
      source: typeof source === "string" ? source.trim() || null : null,
      tags: normalizeTags(tags),
      imageUrl: typeof imageUrl === "string" ? imageUrl.trim() || null : null,
    },
  });
  res.status(201).json(toRecipeView(recipe, req.userId));
});

// PUT /api/recipes/:id — update own recipe (ownership verified)
recipesRouter.put("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.recipe.findFirst({
    where: { id: req.params.id, ownerId: req.userId! },
  });
  if (!existing) return res.status(404).json({ error: "not found" });

  const body = req.body ?? {};
  const {
    title,
    description,
    meal,
    difficulty,
    prepTimeMin,
    cookTimeMin,
    servings,
    calories,
    proteinG,
    carbsG,
    fatG,
    ingredients,
    instructions,
    source,
    tags,
    imageUrl,
  } = body;

  const data: Record<string, unknown> = {};
  if (typeof title === "string" && title.trim()) data.title = title.trim();
  if (typeof description === "string") data.description = description.trim() || null;
  if (meal && VALID_MEALS.includes(meal as Meal)) data.meal = meal;
  if (difficulty && VALID_DIFFICULTIES.includes(difficulty as Difficulty)) data.difficulty = difficulty;
  for (const [key, value] of Object.entries({ prepTimeMin, cookTimeMin, servings, calories, proteinG, carbsG, fatG })) {
    if (typeof value === "number" && Number.isFinite(value)) data[key] = value;
  }
  if (Array.isArray(ingredients)) data.ingredients = JSON.stringify(ingredients);
  if (Array.isArray(instructions)) data.instructions = JSON.stringify(instructions);
  if (typeof source === "string") data.source = source.trim() || null;
  if (typeof imageUrl === "string") data.imageUrl = imageUrl.trim() || null;
  if (imageUrl === null) data.imageUrl = null;
  if (tags !== undefined) data.tags = normalizeTags(tags);

  const updated = await prisma.recipe.update({ where: { id: existing.id }, data });
  if ("imageUrl" in data && data.imageUrl !== existing.imageUrl) await deleteOwnedPhoto(existing.imageUrl, req.userId!);
  res.json(toRecipeView(updated, req.userId));
});

// DELETE /api/recipes/:id — delete own recipe (ownership verified)
recipesRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.recipe.findFirst({
    where: { id: req.params.id, ownerId: req.userId! },
  });
  if (!existing) return res.status(404).json({ error: "not found" });

  await prisma.recipe.delete({ where: { id: existing.id } });

  await deleteOwnedPhoto(existing.imageUrl, req.userId!);

  res.status(204).end();
});

// POST /api/recipes/:id/favorite — toggle favorite
recipesRouter.post("/:id/favorite", async (req: AuthedRequest, res) => {
  const userId = req.userId!;
  const recipe = await prisma.recipe.findUnique({ where: { id: req.params.id } });
  if (!recipe) return res.status(404).json({ error: "not found" });

  const existing = await prisma.recipeFavorite.findFirst({
    where: { ownerId: userId, recipeId: recipe.id },
  });
  if (existing) {
    await prisma.recipeFavorite.delete({ where: { id: existing.id } });
    return res.json({ favorited: false, recipeId: recipe.id });
  }
  await prisma.recipeFavorite.create({ data: { ownerId: userId, recipeId: recipe.id } });
  res.status(201).json({ favorited: true, recipeId: recipe.id });
});

// POST /api/recipes/:id/log — log servings of a recipe as a meal ({ servings?: number, date?: string }).
// Calories come from a Food snapshot row named after the recipe and its per-serving calories, so
// later edits to the recipe never change meals that were already logged. 1 serving = 100 "grams".
recipesRouter.post("/:id/log", async (req: AuthedRequest, res) => {
  const userId = req.userId!;
  const { servings = 1, date } = req.body ?? {};
  if (typeof servings !== "number" || !Number.isFinite(servings) || servings <= 0 || servings > 100) {
    return res.status(400).json({ error: "servings must be a number > 0 and <= 100" });
  }
  let when = new Date();
  if (date !== undefined) {
    when = new Date(date);
    if (typeof date !== "string" || Number.isNaN(when.getTime())) {
      return res.status(400).json({ error: "date must be a valid date string" });
    }
  }
  const recipe = await prisma.recipe.findFirst({
    where: { id: req.params.id, OR: [{ ownerId: null }, { ownerId: userId }] },
  });
  if (!recipe) return res.status(404).json({ error: "not found" });

  const name = `${recipe.title} (${recipe.calories} kcal/serving)`;
  const macros = { proteinPer100: recipe.proteinG, carbsPer100: recipe.carbsG, fatPer100: recipe.fatG };
  let food = await prisma.food.findUnique({ where: { name } });
  if (!food) {
    food = await prisma.food.create({
      data: { name, caloriesPer100: recipe.calories, servingUnit: "serving", recipeId: recipe.id, ...macros },
    });
  } else if (food.proteinPer100 === 0 && food.carbsPer100 === 0 && food.fatPer100 === 0) {
    // legacy snapshot created before macros existed
    food = await prisma.food.update({ where: { id: food.id }, data: macros });
  }
  const meal = await prisma.mealLog.create({
    data: { ownerId: userId, foodId: food.id, grams: servings * 100, date: when },
  });
  res.status(201).json({
    id: meal.id,
    recipeId: recipe.id,
    servings,
    calories: Math.round(recipe.calories * servings),
    proteinG: Math.round(recipe.proteinG * servings * 10) / 10,
    carbsG: Math.round(recipe.carbsG * servings * 10) / 10,
    fatG: Math.round(recipe.fatG * servings * 10) / 10,
    date: meal.date,
  });
});

// POST /api/recipes/upload — multipart photo upload; returns { imageUrl }
recipesRouter.post(
  "/upload",
  (req, res, next) =>
    upload.single("file")(req, res, (err: unknown) => {
      if (!err) return next();
      const e = err as { code?: string; message?: string };
      if (e.code === "LIMIT_FILE_SIZE") return res.status(413).json({ error: "photo too large (max 3 MB)" });
      return res.status(400).json({ error: e.message ?? "upload failed" });
    }),
  async (req: AuthedRequest, res) => {
    if (!req.file) return res.status(400).json({ error: "file (multipart field 'file') required" });
    const photo = await prisma.recipePhoto.create({
      data: { ownerId: req.userId!, mimeType: req.file.mimetype, data: req.file.buffer },
    });
    res.status(200).json({ imageUrl: `${PHOTO_URL_PREFIX}${photo.id}` });
  },
);

export type { RecipeCreateBody };
