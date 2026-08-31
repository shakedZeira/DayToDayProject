import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.userProfile.deleteMany({});
  await prisma.dailyTarget.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "profile@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("PUT profile returns profile + recommendation", async () => {
  const res = await request(app)
    .put("/api/profile")
    .set("Authorization", `Bearer ${token}`)
    .send({
      heightCm: 178,
      weightKg: 75,
      age: 30,
      sex: "male",
      activity: "moderate",
    });
  expect(res.status).toBe(200);
  expect(res.body.profile.weightKg).toBe(75);
  expect(Math.abs(res.body.recommendation.tdee - 2662) <= 10).toBe(true);
  expect(res.body.targetApplied).toBe(false);
});

test("PUT with applyTarget:true sets daily calorie target", async () => {
  const res = await request(app)
    .put("/api/profile")
    .set("Authorization", `Bearer ${token}`)
    .send({
      heightCm: 178,
      weightKg: 75,
      age: 30,
      sex: "male",
      activity: "moderate",
      applyTarget: true,
    });
  expect(res.status).toBe(200);
  expect(res.body.targetApplied).toBe(true);

  const summary = await request(app)
    .get("/api/nutrition/summary")
    .set("Authorization", `Bearer ${token}`);
  expect(summary.status).toBe(200);
  expect(Math.abs(summary.body.target - res.body.recommendation.maintenance) <= 10).toBe(true);
});

test("female profile computes correct tdee", async () => {
  const res = await request(app)
    .put("/api/profile")
    .set("Authorization", `Bearer ${token}`)
    .send({
      heightCm: 165,
      weightKg: 65,
      age: 30,
      sex: "female",
      activity: "light",
    });
  expect(res.status).toBe(200);
  // BMR = 10*65 + 6.25*165 - 5*30 - 161 = 1370.25; TDEE = 1370.25 * 1.375 ≈ 1884
  expect(Math.abs(res.body.recommendation.tdee - 1884) <= 10).toBe(true);
});

test("PUT with invalid heightCm returns 400", async () => {
  const res = await request(app)
    .put("/api/profile")
    .set("Authorization", `Bearer ${token}`)
    .send({ heightCm: 0, weightKg: 75, sex: "male" });
  expect(res.status).toBe(400);
});

test("GET profile with no saved profile returns 404", async () => {
  const res = await request(app)
    .get("/api/profile")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(404);
});

test("GET profile returns saved profile + recommendation", async () => {
  await request(app)
    .put("/api/profile")
    .set("Authorization", `Bearer ${token}`)
    .send({
      heightCm: 178,
      weightKg: 75,
      age: 30,
      sex: "male",
      activity: "moderate",
    });

  const res = await request(app)
    .get("/api/profile")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.profile.heightCm).toBe(178);
  expect(res.body.recommendation.maintenance).toBe(res.body.recommendation.tdee);
});
