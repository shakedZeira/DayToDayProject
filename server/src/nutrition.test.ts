import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

let token = "";

const today = new Date().toISOString().slice(0, 10);

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.food.deleteMany({});
  await prisma.dailyTarget.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "nutrition@example.com", password: "password123" });
  token = reg.body.token as string;
  await seedFoods();
});

async function logChicken(grams: number): Promise<request.Response> {
  const chicken = await prisma.food.findUnique({
    where: { name: "Chicken Breast" },
  });
  return request(app)
    .post("/api/foods/meals")
    .set("Authorization", `Bearer ${token}`)
    .send({ foodId: chicken!.id, grams });
}

test("PUT target, log meals, GET summary computes consumed and remaining", async () => {
  const targetRes = await request(app)
    .put("/api/nutrition/target")
    .set("Authorization", `Bearer ${token}`)
    .send({ calories: 2000 });
  expect(targetRes.status).toBe(200);
  expect(targetRes.body.calories).toBe(2000);

  expect((await logChicken(200)).status).toBe(201);
  expect((await logChicken(100)).status).toBe(201);

  const summary = await request(app)
    .get(`/api/nutrition/summary?date=${today}`)
    .set("Authorization", `Bearer ${token}`);
  expect(summary.status).toBe(200);
  expect(summary.body.target).toBe(2000);
  expect(summary.body.consumed).toBe(495);
  expect(summary.body.remaining).toBe(1505);
  expect(summary.body.meals).toHaveLength(2);
  expect(summary.body.meals[0].foodName).toBe("Chicken Breast");
});

test("GET /summary defaults to today", async () => {
  const targetRes = await request(app)
    .put("/api/nutrition/target")
    .set("Authorization", `Bearer ${token}`)
    .send({ calories: 2000 });
  expect(targetRes.status).toBe(200);

  const summary = await request(app)
    .get("/api/nutrition/summary")
    .set("Authorization", `Bearer ${token}`);
  expect(summary.status).toBe(200);
  expect(summary.body.target).toBe(2000);
  expect(summary.body.date).toBe(today);
});