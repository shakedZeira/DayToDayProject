import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedExercises } from "./exercises.seed";
import { e1rm, matchRepRange, progressingTargetsComplete, updateProgression } from "./progression";

let token = "";

beforeEach(async () => {
  await prisma.workoutSet.deleteMany({});
  await prisma.workoutExercise.deleteMany({});
  await prisma.workout.deleteMany({});
  await prisma.personalRecord.deleteMany({});
  await prisma.exerciseProgression.deleteMany({});
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "progression@example.com", password: "password123" });
  token = reg.body.token as string;
  await seedExercises();
});

test("e1rm(80, 8) ≈ 101.33", () => {
  expect(e1rm(80, 8)).toBeCloseTo(80 * (1 + 8 / 30), 1);
});

test("matchRepRange returns the nearest rep range", () => {
  expect(matchRepRange(8)).toBe(8);
  expect(matchRepRange(12)).toBe(12);
  expect(matchRepRange(13)).toBe(12);
});

test("progressingTargetsComplete requires all sets to hit target reps", () => {
  expect(progressingTargetsComplete(3, 8, [{ reps: 8 }, { reps: 8 }, { reps: 9 }])).toBe(true);
  expect(progressingTargetsComplete(3, 8, [{ reps: 8 }, { reps: 8 }, { reps: 7 }])).toBe(false);
});

test("updateProgression creates PR and bumps targetWeight once target met", async () => {
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "prog2@example.com", password: "password123" });
  const ownerId = reg.body.user.id as string;

  await updateProgression(ownerId, "Bench Press", [
    { weightKg: 80, reps: 8 },
    { weightKg: 80, reps: 8 },
    { weightKg: 80, reps: 8 },
  ]);

  const pr = await prisma.personalRecord.findUnique({
    where: { ownerId_exerciseName_repRange: { ownerId, exerciseName: "Bench Press", repRange: 8 } },
  });
  expect(pr).not.toBeNull();
  expect(pr!.weight).toBe(80);

  const prog = await prisma.exerciseProgression.findUnique({
    where: { ownerId_exerciseName: { ownerId, exerciseName: "Bench Press" } },
  });
  expect(prog).not.toBeNull();
  expect(prog!.targetWeight).toBe(82.5);
});

test("GET /api/workouts/analytics/summary returns totals and non-empty volumeTrend", async () => {
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Push day" });
  const wid = cw.body.id;

  await request(app)
    .post(`/api/workouts/${wid}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ sets: [{ exercise: "Bench Press", weightKg: 60, reps: 8 }] });

  const res = await request(app)
    .get("/api/workouts/analytics/summary")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.totalWorkouts).toBe(1);
  expect(res.body.volumeTrend.length).toBeGreaterThan(0);
  expect(res.body.volumeTrend[0].kg).toBeCloseTo(480, 0);
});

test("PUT /api/progression/:exerciseName updates targets; invalid → 400", async () => {
  const ok = await request(app)
    .put("/api/progression/bench%20press")
    .set("Authorization", `Bearer ${token}`)
    .send({ targetSets: 5, targetReps: 5 });
  expect(ok.status).toBe(200);
  expect(ok.body.targetSets).toBe(5);
  expect(ok.body.targetReps).toBe(5);

  const listing = await request(app)
    .get("/api/progression")
    .set("Authorization", `Bearer ${token}`);
  expect(listing.body).toHaveLength(1);
  expect(listing.body[0].exerciseName).toBe("bench press");

  const bad = await request(app)
    .put("/api/progression/bench%20press")
    .set("Authorization", `Bearer ${token}`)
    .send({ targetSets: 0 });
  expect(bad.status).toBe(400);
});
