import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedExercises } from "./exercises.seed";
import {
  e1rm, matchRepRange, progressingTargetsComplete, updateProgression,
  detectDeload, deloadSuggestions, roundToPlate, weekStartOf, weeklyMuscleVolume, type SessionPoint,
} from "./progression";

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


const sess = (...e: number[]): SessionPoint[] =>
  e.map((v, i) => ({ date: `2026-10-0${i + 1}`, bestE1rm: v, topWeightKg: 100 }));

test("detectDeload: needs 3+ sessions, flags >5% e1RM drop", () => {
  expect(detectDeload(sess(100, 90))).toBeNull();
});

test("detectDeload: e1rm_drop and stalled rules", () => {
  expect(detectDeload(sess(100, 102, 96))?.reason).toBe("e1rm_drop");
  expect(detectDeload(sess(100, 102, 98))).toBeNull(); // 3.9% dip is fine
  expect(detectDeload(sess(100, 100, 100))).toBeNull(); // only 3 sessions: cannot be stalled
  expect(detectDeload(sess(100, 100, 99, 100))?.reason).toBe("stalled");
  expect(detectDeload(sess(100, 101, 99, 100))).toBeNull(); // 101 beats the 100 reference
  expect(detectDeload(sess(100, 100, 101, 102))).toBeNull(); // still progressing
});

test("deloadSuggestions proposes ~90% of the latest top weight rounded to 2.5kg", () => {
  const m = new Map<string, SessionPoint[]>([
    ["Squat", sess(150, 150, 150, 150).map((s) => ({ ...s, topWeightKg: 120 }))],
    ["Curl", sess(30, 31, 32)],
  ]);
  const out = deloadSuggestions(m);
  expect(out).toHaveLength(1);
  expect(out[0]).toMatchObject({ exerciseName: "Squat", reason: "stalled", currentWeightKg: 120, suggestedWeightKg: 107.5 });
  expect(roundToPlate(101)).toBe(100);
});

test("weeklyMuscleVolume buckets working sets by Monday week and muscle, last 8 weeks only", () => {
  const muscles = new Map([["Squat", "Legs"], ["Bench Press", "Chest"]]);
  const now = new Date("2026-11-18T12:00:00Z"); // Wednesday, week of 2026-11-16
  expect(weekStartOf(now)).toBe("2026-11-16");
  expect(weekStartOf(new Date("2026-11-22T23:00:00Z"))).toBe("2026-11-16"); // Sunday
  const rows = weeklyMuscleVolume(
    [
      { date: new Date("2026-11-16T08:00:00Z"), exercise: "Squat", weightKg: 100, reps: 5 },
      { date: new Date("2026-11-17T08:00:00Z"), exercise: "Squat", weightKg: 100, reps: 5 },
      { date: new Date("2026-11-10T08:00:00Z"), exercise: "Bench Press", weightKg: 60, reps: 10 },
      { date: new Date("2026-11-10T08:00:00Z"), exercise: "Mystery", weightKg: 10, reps: 10 },
      { date: new Date("2026-08-01T08:00:00Z"), exercise: "Squat", weightKg: 100, reps: 5 }, // too old
    ],
    muscles,
    now
  );
  expect(rows).toEqual([
    { weekStart: "2026-11-09", muscle: "Chest", sets: 1, kg: 600 },
    { weekStart: "2026-11-09", muscle: "Other", sets: 1, kg: 100 },
    { weekStart: "2026-11-16", muscle: "Legs", sets: 2, kg: 1000 },
  ]);
});

test("GET /api/progression/deload and analytics weekly volume work end to end", async () => {
  const auth = { Authorization: `Bearer ${token}` };
  for (const w of [100, 100, 100, 100]) {
    const wk = await request(app).post("/api/workouts").set(auth).send({ title: "w" });
    const xe = await request(app).post(`/api/workouts/${wk.body.id}/exercises`).set(auth).send({ exerciseName: "Squat" });
    await request(app).post(`/api/workouts/${wk.body.id}/exercises/${xe.body.id}/sets`).set(auth).send({ weightKg: w, reps: 5 });
  }
  const res = await request(app).get("/api/progression/deload").set(auth);
  expect(res.status).toBe(200);
  expect(res.body[0]).toMatchObject({ exerciseName: "Squat", reason: "stalled", suggestedWeightKg: 90 });
  const an = await request(app).get("/api/workouts/analytics/summary").set(auth);
  expect(an.body.weeklyMuscleVolume.length).toBeGreaterThan(0);
  expect(an.body.weeklyMuscleVolume[0]).toHaveProperty("sets");
});
