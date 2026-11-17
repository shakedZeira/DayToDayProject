import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedFoods, SEED_FOODS } from "./foods.seed";

let token = "";

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.food.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "foods@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("seedFoods creates the full global catalog and is idempotent", async () => {
  const first = await seedFoods();
  expect(first).toBe(SEED_FOODS.length);
  expect(first).toBeGreaterThanOrEqual(25);
  const second = await seedFoods();
  expect(second).toBe(0);
});

test("a fresh user can search the shared catalog in English and Hebrew without creating any food", async () => {
  await seedFoods();
  const english = await request(app)
    .post("/api/foods/search")
    .set("Authorization", `Bearer ${token}`)
    .send({ q: "chicken" });
  expect(english.status).toBe(200);
  expect(Array.isArray(english.body)).toBe(true);
  expect(english.body.length).toBeGreaterThanOrEqual(1);
  expect(english.body.some((f: { name: string }) => f.name === "Chicken Breast")).toBe(true);
  expect(english.body[0].nameHe).toBe("חזה עוף");

  const hebrew = await request(app)
    .post("/api/foods/search")
    .set("Authorization", `Bearer ${token}`)
    .send({ q: "עוף" });
  expect(hebrew.status).toBe(200);
  expect(hebrew.body.some((f: { name: string }) => f.name === "Chicken Breast")).toBe(true);
});

test("foods are global: one user's catalog is visible to another fresh user", async () => {
  await seedFoods();
  const other = await request(app)
    .post("/api/auth/register")
    .send({ email: "other@example.com", password: "password123" });
  const otherToken = other.body.token as string;
  const res = await request(app)
    .post("/api/foods/search")
    .set("Authorization", `Bearer ${otherToken}`)
    .send({ q: "banana" });
  expect(res.status).toBe(200);
  expect(res.body.some((f: { name: string }) => f.name === "Banana")).toBe(true);
});

test("POST /api/foods creates a global food with optional Hebrew name and no ownerId", async () => {
  const res = await request(app)
    .post("/api/foods")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "Coconut Water", nameHe: "מי קוקוס", caloriesPer100: 19, servingUnit: "ml" });
  expect(res.status).toBe(201);
  expect(res.body).toMatchObject({
    name: "Coconut Water",
    nameHe: "מי קוקוס",
    caloriesPer100: 19,
    servingUnit: "ml",
  });
  expect(res.body.ownerId).toBeUndefined();
});

test("POST /api/foods/meals logs 200g chicken breast with correct calories", async () => {
  await seedFoods();
  const chicken = await prisma.food.findUnique({
    where: { name: "Chicken Breast" },
  });
  expect(chicken).toBeTruthy();

  const meal = await request(app)
    .post("/api/foods/meals")
    .set("Authorization", `Bearer ${token}`)
    .send({ foodId: chicken!.id, grams: 200 });
  expect(meal.status).toBe(201);
  expect(meal.body.grams).toBe(200);
  const calories = chicken!.caloriesPer100 * (200 / 100);
  expect(calories).toBe(330);
});

test("DELETE /api/foods/meals/:id removes the meal", async () => {
  await seedFoods();
  const chicken = await prisma.food.findUnique({
    where: { name: "Chicken Breast" },
  });
  const meal = await request(app)
    .post("/api/foods/meals")
    .set("Authorization", `Bearer ${token}`)
    .send({ foodId: chicken!.id, grams: 100 });
  expect(meal.status).toBe(201);

  const del = await request(app)
    .delete(`/api/foods/meals/${meal.body.id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);
});

test("GET /api/foods rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/foods");
  expect(res.status).toBe(401);
});
import { setBarcodeFetcher, mapOffProduct } from "./barcode";

const OFF_BODY = {
  status: 1,
  product: {
    product_name: "Choco Spread",
    brands: "Nutella, Ferrero",
    nutriments: { "energy-kcal_100g": 539, proteins_100g: 6.3, carbohydrates_100g: 57.5, fat_100g: 30.9 },
  },
};

test("custom foods are owner-scoped, carry macros, and the global list still works", async () => {
  await seedFoods();
  const created = await request(app)
    .post("/api/foods")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "My Protein Bar", caloriesPer100: 380, proteinPer100: 30, carbsPer100: 35, fatPer100: 12 });
  expect(created.status).toBe(201);
  expect(created.body).toMatchObject({ proteinPer100: 30, carbsPer100: 35, fatPer100: 12, custom: true });

  const mine = await request(app).post("/api/foods/search").set("Authorization", `Bearer ${token}`).send({ q: "protein bar" });
  expect(mine.body.map((f: { name: string }) => f.name)).toContain("My Protein Bar");

  const other = await request(app).post("/api/auth/register").send({ email: "other@example.com", password: "password123" });
  const otherToken = other.body.token as string;
  const theirs = await request(app).get("/api/foods").set("Authorization", `Bearer ${otherToken}`);
  expect(theirs.status).toBe(200);
  expect(theirs.body.some((f: { name: string }) => f.name === "My Protein Bar")).toBe(false);
  expect(theirs.body.some((f: { name: string }) => f.name === "Chicken Breast")).toBe(true);
  const log = await request(app).post("/api/foods/meals").set("Authorization", `Bearer ${otherToken}`).send({ foodId: created.body.id, grams: 100 });
  expect(log.status).toBe(404);
  const del = await request(app).delete(`/api/foods/${created.body.id}`).set("Authorization", `Bearer ${otherToken}`);
  expect(del.status).toBe(404);
  const dup = await request(app).post("/api/foods").set("Authorization", `Bearer ${token}`).send({ name: "My Protein Bar", caloriesPer100: 1 });
  expect(dup.status).toBe(409);
  const bad = await request(app).post("/api/foods").set("Authorization", `Bearer ${token}`).send({ name: "X", caloriesPer100: 1, proteinPer100: -1 });
  expect(bad.status).toBe(400);
});

test("daily summary reports macro totals, per-meal macros and derived targets", async () => {
  const food = await request(app)
    .post("/api/foods")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "Macro Food", caloriesPer100: 200, proteinPer100: 20, carbsPer100: 10, fatPer100: 5 });
  await request(app).post("/api/foods/meals").set("Authorization", `Bearer ${token}`).send({ foodId: food.body.id, grams: 150 });
  const date = new Date().toISOString().slice(0, 10);
  const sum = await request(app).get(`/api/nutrition/summary?date=${date}`).set("Authorization", `Bearer ${token}`);
  expect(sum.status).toBe(200);
  expect(sum.body.macros).toEqual({ proteinG: 30, carbsG: 15, fatG: 7.5 });
  expect(sum.body.meals[0]).toMatchObject({ proteinG: 30, carbsG: 15, fatG: 7.5 });
  expect(sum.body.macroTargets).toEqual({ proteinG: 150, carbsG: 225, fatG: 56 });
});

test("barcode lookup uses the injectable fetcher and never the network", async () => {
  const calls: string[] = [];
  setBarcodeFetcher(async (url) => {
    calls.push(url);
    return { status: 200, json: async () => OFF_BODY };
  });
  try {
    const ok = await request(app).get("/api/foods/barcode/3017620422003").set("Authorization", `Bearer ${token}`);
    expect(ok.status).toBe(200);
    expect(calls).toEqual(["https://world.openfoodfacts.org/api/v2/product/3017620422003.json"]);
    expect(ok.body.source).toBe("openfoodfacts");
    expect(ok.body.food).toMatchObject({ name: "Choco Spread (Nutella)", caloriesPer100: 539, proteinPer100: 6.3, carbsPer100: 57.5, fatPer100: 30.9, barcode: "3017620422003" });

    await request(app).post("/api/foods").set("Authorization", `Bearer ${token}`).send({ ...ok.body.food });
    const again = await request(app).get("/api/foods/barcode/3017620422003").set("Authorization", `Bearer ${token}`);
    expect(again.body.source).toBe("mine");
    expect(calls.length).toBe(1);

    expect((await request(app).get("/api/foods/barcode/abc").set("Authorization", `Bearer ${token}`)).status).toBe(400);

    setBarcodeFetcher(async () => ({ status: 404, json: async () => ({ status: 0 }) }));
    expect((await request(app).get("/api/foods/barcode/1234567890123").set("Authorization", `Bearer ${token}`)).status).toBe(404);
    setBarcodeFetcher(async () => { throw new Error("offline"); });
    expect((await request(app).get("/api/foods/barcode/1234567890124").set("Authorization", `Bearer ${token}`)).status).toBe(502);
  } finally {
    setBarcodeFetcher(null);
  }
});

test("mapOffProduct handles kJ-only energy and unusable products", () => {
  const m = mapOffProduct("111111", { status: 1, product: { product_name: "Bar", nutriments: { energy_100g: 1000 } } });
  expect(m?.caloriesPer100).toBe(239);
  expect(mapOffProduct("1", { status: 0 })).toBeNull();
  expect(mapOffProduct("1", { status: 1, product: { nutriments: {} } })).toBeNull();
});
