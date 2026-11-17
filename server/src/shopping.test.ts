import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let otherToken = "";

async function register(email: string): Promise<string> {
  const res = await request(app).post("/api/auth/register").send({ email, password: "password123" });
  expect(res.status).toBe(201);
  return res.body.token as string;
}

const auth = (t: string) => ({ Authorization: `Bearer ${t}` });

async function makeRecipe(t: string, overrides: Record<string, unknown> = {}) {
  const res = await request(app).post("/api/recipes").set(auth(t)).send({
    title: "Pancakes",
    meal: "breakfast",
    difficulty: "beginner",
    prepTimeMin: 5,
    cookTimeMin: 10,
    servings: 2,
    calories: 300,
    proteinG: 8,
    carbsG: 40,
    fatG: 6,
    ingredients: [
      { name: "Flour", amount: 200, unit: "g" },
      { name: "Milk", amount: 300, unit: "ml" },
    ],
    instructions: [{ step: 1, text: "Mix" }],
    ...overrides,
  });
  expect(res.status).toBe(201);
  return res.body as { id: string };
}

beforeEach(async () => {
  await prisma.mealPlanEntry.deleteMany({});
  await prisma.shoppingItem.deleteMany({});
  await prisma.pantryItem.deleteMany({});
  await prisma.recipeFavorite.deleteMany({});
  await prisma.recipe.deleteMany({});
  await prisma.user.deleteMany({});
  token = await register("shop1@example.com");
  otherToken = await register("shop2@example.com");
});

test("requires auth", async () => {
  expect((await request(app).get("/api/shopping")).status).toBe(401);
  expect((await request(app).get("/api/mealplan")).status).toBe(401);
});

test("manual item CRUD, check and clear checked", async () => {
  const a = await request(app).post("/api/shopping").set(auth(token)).send({ name: "Eggs", amount: 6 });
  expect(a.status).toBe(201);
  expect(a.body).toMatchObject({ name: "Eggs", amount: 6, unit: "", checked: false });
  await request(app).post("/api/shopping").set(auth(token)).send({ name: "Salt" });

  const chk = await request(app).patch(`/api/shopping/${a.body.id}`).set(auth(token)).send({ checked: true });
  expect(chk.body.checked).toBe(true);
  const unchk = await request(app).patch(`/api/shopping/${a.body.id}`).set(auth(token)).send({ checked: false });
  expect(unchk.body.checked).toBe(false);
  await request(app).patch(`/api/shopping/${a.body.id}`).set(auth(token)).send({ checked: true });

  const cleared = await request(app).delete("/api/shopping/checked").set(auth(token));
  expect(cleared.body).toEqual({ deleted: 1 });
  const list = await request(app).get("/api/shopping").set(auth(token));
  expect(list.body.map((i: { name: string }) => i.name)).toEqual(["Salt"]);

  const del = await request(app).delete(`/api/shopping/${list.body[0].id}`).set(auth(token));
  expect(del.status).toBe(204);
  expect((await request(app).delete(`/api/shopping/${list.body[0].id}`).set(auth(token))).status).toBe(404);
});

test("validates input", async () => {
  expect((await request(app).post("/api/shopping").set(auth(token)).send({ name: " " })).status).toBe(400);
  expect((await request(app).post("/api/shopping").set(auth(token)).send({ name: "x", amount: -1 })).status).toBe(400);
  const r = await makeRecipe(token);
  expect((await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({ servings: 0 })).status).toBe(400);
});

test("adds recipe ingredients scaled by servings and merges duplicates by name+unit", async () => {
  const r = await makeRecipe(token); // serves 2
  const first = await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({ servings: 4 });
  expect(first.status).toBe(201);
  const byName = (b: { name: string }[]) => Object.fromEntries(b.map((i: any) => [i.name, i]));
  expect(byName(first.body).Flour.amount).toBe(400);
  expect(byName(first.body).Milk.amount).toBe(600);
  expect(byName(first.body).Flour.recipeId).toBe(r.id);

  // Second add (default servings) merges into the same rows.
  const second = await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({});
  expect(second.body).toHaveLength(2);
  expect(byName(second.body).Flour.amount).toBe(600);

  // Same name, different unit stays separate; name match is case-insensitive.
  const r2 = await makeRecipe(token, {
    title: "Bread",
    ingredients: [{ name: "flour", amount: 2, unit: "cup" }, { name: "FLOUR", amount: 50, unit: "g" }],
  });
  const third = await request(app).post(`/api/shopping/recipe/${r2.id}`).set(auth(token)).send({});
  expect(third.body).toHaveLength(3);
  expect(third.body.find((i: any) => i.unit === "g").amount).toBe(650);
});

test("checked items are not merged into; a fresh row is created", async () => {
  const r = await makeRecipe(token);
  const added = await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({});
  const flour = added.body.find((i: any) => i.name === "Flour");
  await request(app).patch(`/api/shopping/${flour.id}`).set(auth(token)).send({ checked: true });
  const again = await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({});
  expect(again.body.filter((i: any) => i.name === "Flour")).toHaveLength(2);
});

test("recipe visibility: shared and own ok, another user's private recipe is 404", async () => {
  const mine = await makeRecipe(otherToken);
  const res = await request(app).post(`/api/shopping/recipe/${mine.id}`).set(auth(token)).send({});
  expect(res.status).toBe(404);

  const shared = await prisma.recipe.create({
    data: {
      ownerId: null, title: "Shared", meal: "dinner", difficulty: "beginner", prepTimeMin: 1, cookTimeMin: 1,
      servings: 1, calories: 1, proteinG: 1, carbsG: 1, fatG: 1,
      ingredients: JSON.stringify([{ name: "Rice", amount: 1, unit: "cup" }]), instructions: "[]",
    },
  });
  const ok = await request(app).post(`/api/shopping/recipe/${shared.id}`).set(auth(token)).send({});
  expect(ok.status).toBe(201);
  expect(ok.body[0].name).toBe("Rice");
});

test("lists are owner-scoped", async () => {
  const a = await request(app).post("/api/shopping").set(auth(token)).send({ name: "Eggs" });
  expect((await request(app).get("/api/shopping").set(auth(otherToken))).body).toEqual([]);
  expect((await request(app).patch(`/api/shopping/${a.body.id}`).set(auth(otherToken)).send({ checked: true })).status).toBe(404);
  expect((await request(app).delete(`/api/shopping/${a.body.id}`).set(auth(otherToken))).status).toBe(404);
  await request(app).delete("/api/shopping/checked").set(auth(otherToken));
  expect((await request(app).get("/api/shopping").set(auth(token))).body).toHaveLength(1);
});

test("pantry CRUD is owner-scoped", async () => {
  const p = await request(app).post("/api/shopping/pantry").set(auth(token)).send({ name: "Flour", amount: 500, unit: "g" });
  expect(p.status).toBe(201);
  const upd = await request(app).patch(`/api/shopping/pantry/${p.body.id}`).set(auth(token)).send({ amount: 100 });
  expect(upd.body).toMatchObject({ name: "Flour", amount: 100, unit: "g" });
  expect((await request(app).get("/api/shopping/pantry").set(auth(otherToken))).body).toEqual([]);
  expect((await request(app).patch(`/api/shopping/pantry/${p.body.id}`).set(auth(otherToken)).send({ amount: 1 })).status).toBe(404);
  expect((await request(app).delete(`/api/shopping/pantry/${p.body.id}`).set(auth(otherToken))).status).toBe(404);
  expect((await request(app).delete(`/api/shopping/pantry/${p.body.id}`).set(auth(token))).status).toBe(204);
  expect((await request(app).get("/api/shopping/pantry").set(auth(token))).body).toEqual([]);
});

test("skipPantry subtracts pantry stock and skips untracked pantry items", async () => {
  await request(app).post("/api/shopping/pantry").set(auth(token)).send({ name: "flour", amount: 150, unit: "g" });
  await request(app).post("/api/shopping/pantry").set(auth(token)).send({ name: "Milk" });
  const r = await makeRecipe(token);
  const res = await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({ skipPantry: true });
  expect(res.body).toHaveLength(1);
  expect(res.body[0]).toMatchObject({ name: "Flour", amount: 50 });
});

test("meal plan: set, replace, list in range, delete", async () => {
  const r1 = await makeRecipe(token);
  const r2 = await makeRecipe(token, { title: "Omelette" });
  const put = await request(app).put("/api/mealplan/2026-11-20/breakfast").set(auth(token)).send({ recipeId: r1.id, servings: 3 });
  expect(put.status).toBe(200);
  expect(put.body).toMatchObject({ date: "2026-11-20", slot: "breakfast", recipeTitle: "Pancakes", servings: 3 });
  await request(app).put("/api/mealplan/2026-11-20/dinner").set(auth(token)).send({ recipeId: r2.id });
  await request(app).put("/api/mealplan/2026-11-27/lunch").set(auth(token)).send({ recipeId: r2.id });

  const replaced = await request(app).put("/api/mealplan/2026-11-20/breakfast").set(auth(token)).send({ recipeId: r2.id });
  expect(replaced.body.recipeTitle).toBe("Omelette");
  expect(replaced.body.servings).toBeNull();

  const week = await request(app).get("/api/mealplan?from=2026-11-20&to=2026-11-26").set(auth(token));
  expect(week.body.map((e: any) => e.slot)).toEqual(["breakfast", "dinner"]);
  expect((await request(app).get("/api/mealplan?from=2026-11-20&to=2026-11-30").set(auth(token))).body).toHaveLength(3);
  expect((await request(app).get("/api/mealplan?from=2026-11-20&to=2026-11-30").set(auth(otherToken))).body).toEqual([]);

  expect((await request(app).delete("/api/mealplan/2026-11-20/dinner").set(auth(token))).status).toBe(204);
  expect((await request(app).delete("/api/mealplan/2026-11-20/dinner").set(auth(token))).status).toBe(404);
});

test("meal plan validation and recipe visibility", async () => {
  const mine = await makeRecipe(otherToken);
  const r = await makeRecipe(token);
  expect((await request(app).put("/api/mealplan/2026-11-20/brunch").set(auth(token)).send({ recipeId: r.id })).status).toBe(400);
  expect((await request(app).put("/api/mealplan/20-11-2026/lunch").set(auth(token)).send({ recipeId: r.id })).status).toBe(400);
  expect((await request(app).put("/api/mealplan/2026-11-20/lunch").set(auth(token)).send({})).status).toBe(400);
  expect((await request(app).put("/api/mealplan/2026-11-20/lunch").set(auth(token)).send({ recipeId: mine.id })).status).toBe(404);
});

test("meal plan to shopping list scales by entry servings and merges", async () => {
  const r = await makeRecipe(token); // serves 2: flour 200g
  await request(app).put("/api/mealplan/2026-11-20/breakfast").set(auth(token)).send({ recipeId: r.id, servings: 4 });
  await request(app).put("/api/mealplan/2026-11-21/breakfast").set(auth(token)).send({ recipeId: r.id });
  await request(app).put("/api/mealplan/2026-12-05/breakfast").set(auth(token)).send({ recipeId: r.id });
  const res = await request(app).post("/api/mealplan/shopping").set(auth(token)).send({ from: "2026-11-20", to: "2026-11-26" });
  expect(res.status).toBe(201);
  expect(res.body.find((i: any) => i.name === "Flour").amount).toBe(600);
  expect((await request(app).post("/api/mealplan/shopping").set(auth(token)).send({ from: "x", to: "y" })).status).toBe(400);
});

test("deleting a recipe removes its plan entries and detaches shopping items", async () => {
  const r = await makeRecipe(token);
  await request(app).put("/api/mealplan/2026-11-20/lunch").set(auth(token)).send({ recipeId: r.id });
  await request(app).post(`/api/shopping/recipe/${r.id}`).set(auth(token)).send({});
  expect((await request(app).delete(`/api/recipes/${r.id}`).set(auth(token))).status).toBeLessThan(300);
  expect((await request(app).get("/api/mealplan?from=2026-11-20&to=2026-11-20").set(auth(token))).body).toEqual([]);
  const list = await request(app).get("/api/shopping").set(auth(token));
  expect(list.body).toHaveLength(2);
  expect(list.body[0].recipeId).toBeNull();
});
