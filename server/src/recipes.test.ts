import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let otherToken = "";

async function register(email: string): Promise<string> {
  const res = await request(app)
    .post("/api/auth/register")
    .send({ email, password: "password123" });
  expect(res.status).toBe(201);
  return res.body.token as string;
}

const baseRecipe = {
  title: "Test Oatmeal",
  description: "A test breakfast",
  meal: "breakfast" as const,
  difficulty: "beginner" as const,
  prepTimeMin: 5,
  cookTimeMin: 10,
  servings: 1,
  calories: 250,
  proteinG: 10,
  carbsG: 40,
  fatG: 5,
  ingredients: [{ name: "Oats", amount: 1, unit: "cup" }, { name: "Milk", amount: 200, unit: "ml" }],
  instructions: [{ step: 1, text: "Cook oats in milk" }, { step: 2, text: "Serve warm" }],
};

async function createRecipe(authToken: string, overrides: Record<string, unknown> = {}) {
  const payload = { ...baseRecipe, ...overrides };
  const res = await request(app)
    .post("/api/recipes")
    .set("Authorization", `Bearer ${authToken}`)
    .send(payload);
  return res;
}

beforeEach(async () => {
  await prisma.recipeFavorite.deleteMany({});
  await prisma.recipe.deleteMany({});
  await prisma.recipePhoto.deleteMany({});
  await prisma.user.deleteMany({});
  token = await register("user1@example.com");
  otherToken = await register("user2@example.com");
});

test("creates a recipe and parses ingredients/instructions on read", async () => {
  const res = await createRecipe(token);
  expect(res.status).toBe(201);
  expect(res.body.title).toBe("Test Oatmeal");
  expect(res.body.ownerId).toBeUndefined();
  expect(res.body.ingredients).toEqual(baseRecipe.ingredients);
  expect(res.body.instructions).toEqual(baseRecipe.instructions);
  expect(res.body.isFavorited).toBe(false);
  expect(res.body.favoriteCount).toBe(0);

  const list = await request(app)
    .get("/api/recipes?mine=true")
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
});

test("lists shared recipes with isFavorite and favoriteCount", async () => {
  await prisma.recipe.create({
    data: {
      ownerId: null,
      title: "Shared Pancakes",
      meal: "breakfast",
      difficulty: "beginner",
      prepTimeMin: 5,
      cookTimeMin: 10,
      servings: 2,
      calories: 300,
      proteinG: 15,
      carbsG: 35,
      fatG: 8,
      ingredients: JSON.stringify([{ name: "Flour", amount: 1, unit: "cup" }]),
      instructions: JSON.stringify([{ step: 1, text: "Mix" }]),
      tags: "breakfast,shared",
    },
  });

  const list = await request(app).get("/api/recipes").set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
  expect(list.body[0].isFavorited).toBe(false);
  expect(list.body[0].favoriteCount).toBe(0);
  expect(list.body[0].tags).toEqual(["breakfast", "shared"]);
});

test("GET /api/recipes/:id returns a single recipe with isFavorite flag", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const res = await request(app)
    .get(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.id).toBe(id);
  expect(res.body.isFavorited).toBe(false);
  expect(res.body.favoriteCount).toBe(0);

  const missing = await request(app)
    .get("/api/recipes/does-not-exist")
    .set("Authorization", `Bearer ${token}`);
  expect(missing.status).toBe(404);
});

test("?mine=true returns only the authed user's recipes; ?favorites=true returns favorites", async () => {
  await createRecipe(token);
  await createRecipe(otherToken);
  await createRecipe(token, { title: "Second Recipe" });

  const mine = await request(app)
    .get("/api/recipes?mine=true")
    .set("Authorization", `Bearer ${token}`);
  expect(mine.status).toBe(200);
  expect(mine.body).toHaveLength(2);

  const shared = await request(app)
    .get("/api/recipes?meal=lunch")
    .set("Authorization", `Bearer ${token}`);
  expect(shared.status).toBe(200);
});

test("?meal= filters recipes by meal", async () => {
  await createRecipe(token, { meal: "lunch", title: "Lunch Meal" });
  await createRecipe(token, { meal: "dinner", title: "Dinner Meal" });

  const lunch = await request(app)
    .get("/api/recipes?mine=true&meal=lunch")
    .set("Authorization", `Bearer ${token}`);
  expect(lunch.status).toBe(200);
  expect(lunch.body).toHaveLength(1);
  expect(lunch.body[0].meal).toBe("lunch");
});

test("favorite toggle and favorites list", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const add = await request(app)
    .post(`/api/recipes/${id}/favorite`)
    .set("Authorization", `Bearer ${token}`);
  expect(add.status).toBe(201);
  expect(add.body.favorited).toBe(true);

  const favorites = await request(app)
    .get("/api/recipes/favorites")
    .set("Authorization", `Bearer ${token}`);
  expect(favorites.status).toBe(200);
  expect(favorites.body).toHaveLength(1);
  expect(favorites.body[0].isFavorited).toBe(true);
  expect(favorites.body[0].favoriteCount).toBe(1);

  const remove = await request(app)
    .post(`/api/recipes/${id}/favorite`)
    .set("Authorization", `Bearer ${token}`);
  expect(remove.status).toBe(200);
  expect(remove.body.favorited).toBe(false);

  const after = await request(app)
    .get("/api/recipes/favorites")
    .set("Authorization", `Bearer ${token}`);
  expect(after.body).toHaveLength(0);
});

test("favoriting a nonexistent recipe returns 404", async () => {
  const res = await request(app)
    .post("/api/recipes/does-not-exist/favorite")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(404);
});

test("updates own recipe", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const res = await request(app)
    .put(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Updated Oatmeal", servings: 4 });
  expect(res.status).toBe(200);
  expect(res.body.title).toBe("Updated Oatmeal");
  expect(res.body.servings).toBe(4);
  expect(res.body.ingredients).toEqual(baseRecipe.ingredients);

  const fetch = await request(app)
    .get(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(fetch.body.title).toBe("Updated Oatmeal");
});

test("cannot update another user's recipe", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const res = await request(app)
    .put(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${otherToken}`)
    .send({ title: "Hacked" });
  expect(res.status).toBe(404);
});

test("deletes own recipe", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const del = await request(app)
    .delete(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);

  const mine = await request(app)
    .get("/api/recipes?mine=true")
    .set("Authorization", `Bearer ${token}`);
  expect(mine.body).toHaveLength(0);
});

test("cannot delete another user's recipe", async () => {
  const created = await createRecipe(token);
  const id = created.body.id;

  const del = await request(app)
    .delete(`/api/recipes/${id}`)
    .set("Authorization", `Bearer ${otherToken}`);
  expect(del.status).toBe(404);
});

test("upload photo stores bytes in Postgres and serves them without auth", async () => {
  const bytes = Buffer.from("fake image bytes");
  const res = await request(app)
    .post("/api/recipes/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", bytes, { filename: "photo.jpg", contentType: "image/jpeg" });
  expect(res.status).toBe(200);
  expect(res.body.imageUrl).toMatch(/^\/api\/recipes\/uploads\/[^/]+$/);
  const img = await request(app).get(res.body.imageUrl);
  expect(img.status).toBe(200);
  expect(img.headers["content-type"]).toContain("image/jpeg");
  expect(Buffer.from(img.body).equals(bytes)).toBe(true);
});

test("upload rejects photos over 3 MB", async () => {
  const res = await request(app)
    .post("/api/recipes/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", Buffer.alloc(3 * 1024 * 1024 + 1), { filename: "big.jpg", contentType: "image/jpeg" });
  expect(res.status).toBe(413);
});

test("deleting a recipe removes its uploaded photo", async () => {
  const up = await request(app)
    .post("/api/recipes/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", Buffer.from("x"), { filename: "p.png", contentType: "image/png" });
  const created = await createRecipe(token, { imageUrl: up.body.imageUrl });
  expect(created.status).toBe(201);
  await request(app).delete(`/api/recipes/${created.body.id}`).set("Authorization", `Bearer ${token}`);
  expect((await request(app).get(up.body.imageUrl)).status).toBe(404);
});

test("upload rejects non-image file", async () => {
  const res = await request(app)
    .post("/api/recipes/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", Buffer.from("not an image"), "doc.txt");
  expect(res.status).toBeGreaterThanOrEqual(400);
});

test("create rejects invalid input", async () => {
  const res = await request(app)
    .post("/api/recipes")
    .set("Authorization", `Bearer ${token}`)
    .send({ ...baseRecipe, title: "" });
  expect(res.status).toBe(400);
});

test("rejects unauthenticated access", async () => {
  const list = await request(app).get("/api/recipes");
  expect(list.status).toBe(401);
  const single = await request(app).get("/api/recipes/some-id");
  expect(single.status).toBe(401);
  const create = await request(app).post("/api/recipes").send(baseRecipe);
  expect(create.status).toBe(401);
});

test("POST /api/recipes/:id/log logs servings into the nutrition summary and hides snapshot foods", async () => {
  token = await register("recipe-log@example.com");
  const created = await createRecipe(token, { calories: 250 });
  const id = created.body.id as string;

  const res = await request(app)
    .post(`/api/recipes/${id}/log`)
    .set("Authorization", `Bearer ${token}`)
    .send({ servings: 2 });
  expect(res.status).toBe(201);
  expect(res.body.calories).toBe(500);

  const summary = await request(app).get("/api/nutrition/summary").set("Authorization", `Bearer ${token}`);
  expect(summary.body.consumed).toBe(500);
  expect(summary.body.meals[0].servings).toBe(2);
  const base = created.body;
  expect(summary.body.macros.proteinG).toBeCloseTo(base.proteinG * 2, 1);
  expect(summary.body.macros.carbsG).toBeCloseTo(base.carbsG * 2, 1);
  expect(summary.body.macros.fatG).toBeCloseTo(base.fatG * 2, 1);
  expect(res.body.proteinG).toBeCloseTo(base.proteinG * 2, 1);

  const foods = await request(app).get("/api/foods").set("Authorization", `Bearer ${token}`);
  expect(foods.body.some((f: { name: string }) => f.name.includes("Test Oatmeal"))).toBe(false);

  // editing the recipe later does not change the already-logged meal
  await prisma.recipe.update({ where: { id }, data: { calories: 900 } });
  const again = await request(app).get("/api/nutrition/summary").set("Authorization", `Bearer ${token}`);
  expect(again.body.consumed).toBe(500);
});

test("POST /api/recipes/:id/log validates input and recipe visibility", async () => {
  token = await register("recipe-log2@example.com");
  otherToken = await register("recipe-log3@example.com");
  const id = (await createRecipe(token)).body.id as string;

  const bad = await request(app).post(`/api/recipes/${id}/log`).set("Authorization", `Bearer ${token}`).send({ servings: 0 });
  expect(bad.status).toBe(400);
  const hidden = await request(app).post(`/api/recipes/${id}/log`).set("Authorization", `Bearer ${otherToken}`).send({});
  expect(hidden.status).toBe(404);
});
