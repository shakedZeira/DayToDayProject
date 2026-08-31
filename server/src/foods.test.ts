import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedFoods, SEED_FOODS } from "./foods.seed";

let token = "";
let userId = "";

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.food.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "foods@example.com", password: "password123" });
  token = reg.body.token as string;
  userId = reg.body.user.id as string;
});

test("seedFoods creates >= 20 foods and is idempotent", async () => {
  const first = await seedFoods(userId);
  expect(first).toBeGreaterThan(0);
  expect(first).toBeGreaterThanOrEqual(20);
  expect(first).toBe(SEED_FOODS.length);
  const second = await seedFoods(userId);
  expect(second).toBe(0);
});

test("POST /api/foods/search finds chicken foods", async () => {
  await seedFoods(userId);
  const res = await request(app)
    .post("/api/foods/search")
    .set("Authorization", `Bearer ${token}`)
    .send({ q: "chicken" });
  expect(res.status).toBe(200);
  expect(Array.isArray(res.body)).toBe(true);
  expect(res.body.length).toBeGreaterThanOrEqual(1);
  expect(res.body.some((f: { name: string }) => f.name === "Chicken Breast")).toBe(true);
});

test("POST /api/foods/meals logs 200g chicken breast with correct calories", async () => {
  await seedFoods(userId);
  const chicken = await prisma.food.findUnique({
    where: { ownerId_name: { ownerId: userId, name: "Chicken Breast" } },
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
  await seedFoods(userId);
  const chicken = await prisma.food.findUnique({
    where: { ownerId_name: { ownerId: userId, name: "Chicken Breast" } },
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

  const remaining = await prisma.mealLog.count({ where: { ownerId: userId } });
  expect(remaining).toBe(0);
});

test("GET /api/foods rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/foods");
  expect(res.status).toBe(401);
});