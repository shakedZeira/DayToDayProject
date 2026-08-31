import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedExercises } from "./exercises.seed";

let token = "";

beforeEach(async () => {
  await prisma.exercise.deleteMany({});
  await prisma.user.deleteMany({});
  await seedExercises();
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "exercises@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("GET /api/exercises returns the long shared library even for a fresh user", async () => {
  const res = await request(app)
    .get("/api/exercises")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.length).toBeGreaterThan(40);
  expect(res.body.every((e: { isCustom: boolean }) => e.isCustom === false)).toBe(true);
});

test("GET /api/exercises?q=bench returns Bench Press", async () => {
  const res = await request(app)
    .get("/api/exercises?q=bench")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.some((e: { name: string }) => e.name === "Bench Press")).toBe(true);
  expect(res.body.some((e: { name: string }) => e.name === "Squat")).toBe(false);
});

test("POST /api/exercises creates a custom exercise visible to the user", async () => {
  const created = await request(app)
    .post("/api/exercises")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "My Custom Move", muscleGroup: "Arms", isCompound: false });
  expect(created.status).toBe(201);
  expect(created.body).toMatchObject({
    name: "My Custom Move",
    muscleGroup: "Arms",
    isCompound: false,
    isCustom: true,
  });

  const list = await request(app)
    .get("/api/exercises")
    .set("Authorization", `Bearer ${token}`);
  const match = list.body.find((e: { name: string }) => e.name === "My Custom Move");
  expect(match).toBeTruthy();
  expect(match.isCustom).toBe(true);
});

test("POST /api/exercises rejects invalid input", async () => {
  const blank = await request(app)
    .post("/api/exercises")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "   " });
  expect(blank.status).toBe(400);

  const badGroup = await request(app)
    .post("/api/exercises")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "Weird Move", muscleGroup: "NotARealGroup" });
  expect(badGroup.status).toBe(400);
});

test("POST /api/exercises rejects duplicate custom exercise with 409", async () => {
  await request(app)
    .post("/api/exercises")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "Duplicate Move" });
  const dup = await request(app)
    .post("/api/exercises")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "Duplicate Move" });
  expect(dup.status).toBe(409);
});

test("GET /api/exercises rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/exercises");
  expect(res.status).toBe(401);
});
