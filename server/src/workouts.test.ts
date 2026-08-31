import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let userId = "";

beforeEach(async () => {
  await prisma.workoutSet.deleteMany({});
  await prisma.workout.deleteMany({});
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "workouts@example.com", password: "password123" });
  token = reg.body.token as string;
  userId = reg.body.user.id as string;
});

test("create a workout and append 2 sets", async () => {
  const create = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Push day" });
  expect(create.status).toBe(201);
  const id = create.body.id;

  const append = await request(app)
    .post(`/api/workouts/${id}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ sets: [
      { exercise: "Bench Press", weightKg: 60, reps: 8 },
      { exercise: "Bench Press", weightKg: 62.5, reps: 6 },
    ] });
  expect(append.status).toBe(201);
  expect(append.body).toHaveLength(2);

  const get = await request(app)
    .get(`/api/workouts/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(get.status).toBe(200);
  expect(get.body.sets).toHaveLength(2);
});

test("rejects bad set payload (weightKg 0)", async () => {
  const create = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Leg day" });
  const id = create.body.id;

  const append = await request(app)
    .post(`/api/workouts/${id}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ sets: [{ exercise: "Squat", weightKg: 0, reps: 5 }] });
  expect(append.status).toBe(400);
});

test("progressiveSuggestion bumps weight when target completed", async () => {
  const { progressiveSuggestion } = await import("./workouts");
  const result = await progressiveSuggestion([
    { exercise: "Bench Press", weightKg: 60, reps: 8 },
    { exercise: "Bench Press", weightKg: 60, reps: 9 },
    { exercise: "Bench Press", weightKg: 60, reps: 8 },
  ]);
  expect(result[0].reason).toBe("completed_target");
  expect(result[0].suggestedNextKg).toBe(62.5);
});

test("progressiveSuggestion holds weight when not met", async () => {
  const { progressiveSuggestion } = await import("./workouts");
  const result = await progressiveSuggestion([
    { exercise: "Deadlift", weightKg: 80, reps: 5 },
    { exercise: "Deadlift", weightKg: 80, reps: 5 },
  ]);
  expect(result[0].reason).toBe("not_yet");
  expect(result[0].suggestedNextKg).toBe(80);
});

test("unauthenticated GET /api/workouts returns 401", async () => {
  const res = await request(app).get("/api/workouts");
  expect(res.status).toBe(401);
});

test("weekly-goals hook increments an auto-source workout goal", async () => {
  const goal = await prisma.weeklyGoal.create({
    data: {
      ownerId: userId,
      title: "Workout 3x",
      targetCount: 3,
      autoSource: "workout",
    },
  });

  await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Push day" });

  const count = await prisma.goalEvent.count({ where: { goalId: goal.id } });
  expect(count).toBe(1);
});
