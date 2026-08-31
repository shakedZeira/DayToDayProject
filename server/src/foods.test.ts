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