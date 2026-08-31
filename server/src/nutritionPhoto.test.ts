import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "photo@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("analyze-photo returns a stub estimate for an uploaded image", async () => {
  const res = await request(app)
    .post("/api/nutrition/analyze-photo")
    .set("Authorization", `Bearer ${token}`)
    .attach("image", Buffer.from("fake-jpeg-bytes"), "meal.jpg");
  expect(res.status).toBe(200);
  expect(res.body.provider).toBe("stub");
  expect(typeof res.body.totalCalories).toBe("number");
  expect(res.body.totalCalories).toBeGreaterThan(0);
  expect(res.body.disclaimer).toMatch(/rough/i);
});

test("analyze-photo without a file field returns 400", async () => {
  const res = await request(app)
    .post("/api/nutrition/analyze-photo")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(400);
});
