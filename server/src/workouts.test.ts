import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedExercises } from "./exercises.seed";
import { startOfWeek } from "./goals";

function dateKey(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

let token = "";
let userId = "";

beforeEach(async () => {
  await prisma.workoutSet.deleteMany({});
  await prisma.workoutExercise.deleteMany({});
  await prisma.workout.deleteMany({});
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "workouts@example.com", password: "password123" });
  token = reg.body.token as string;
  userId = reg.body.user.id as string;
  await seedExercises();
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

test("POST /:id/exercises with shared exerciseId returns 201 with exerciseName and empty sets", async () => {
  const bench = await prisma.exercise.findFirst({ where: { name: "Bench Press" } });
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Test" });
  const wid = cw.body.id;

  const res = await request(app)
    .post(`/api/workouts/${wid}/exercises`)
    .set("Authorization", `Bearer ${token}`)
    .send({ exerciseId: bench!.id });
  expect(res.status).toBe(201);
  expect(res.body.exerciseName).toBe("Bench Press");
  expect(res.body.exerciseId).toBe(bench!.id);
  expect(res.body.sets).toEqual([]);
  expect(res.body.order).toBe(1);
});

test("POST /:id/exercises with free-text exerciseName returns 201", async () => {
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Test" });
  const wid = cw.body.id;

  const res = await request(app)
    .post(`/api/workouts/${wid}/exercises`)
    .set("Authorization", `Bearer ${token}`)
    .send({ exerciseName: "My Custom Move" });
  expect(res.status).toBe(201);
  expect(res.body.exerciseName).toBe("My Custom Move");
  expect(res.body.exerciseId).toBeNull();
});

test("POST /:id/exercises/:xeId/sets with setType warmup returns 201 and GET includes it", async () => {
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Test" });
  const wid = cw.body.id;

  const xe = await request(app)
    .post(`/api/workouts/${wid}/exercises`)
    .set("Authorization", `Bearer ${token}`)
    .send({ exerciseName: "Squat" });
  const xeId = xe.body.id;

  const setRes = await request(app)
    .post(`/api/workouts/${wid}/exercises/${xeId}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ weightKg: 80, reps: 8, setType: "warmup" });
  expect(setRes.status).toBe(201);
  expect(setRes.body.setType).toBe("warmup");
  expect(setRes.body.order).toBe(1);

  const detail = await request(app)
    .get(`/api/workouts/${wid}`)
    .set("Authorization", `Bearer ${token}`);
  expect(detail.status).toBe(200);
  expect(detail.body.exercises).toHaveLength(1);
  expect(detail.body.exercises[0].sets[0].setType).toBe("warmup");
  expect(detail.body.exercises[0].sets[0].weightKg).toBe(80);
});

test("POST /:id/exercises/:xeId/sets with invalid setType returns 400", async () => {
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Test" });
  const wid = cw.body.id;

  const xe = await request(app)
    .post(`/api/workouts/${wid}/exercises`)
    .set("Authorization", `Bearer ${token}`)
    .send({ exerciseName: "Squat" });

  const setRes = await request(app)
    .post(`/api/workouts/${wid}/exercises/${xe.body.id}/sets`)
    .set("Authorization", `Bearer ${token}`)
    .send({ weightKg: 80, reps: 8, setType: "invalid_type" });
  expect(setRes.status).toBe(400);
});

test("POST /:id/exercises/:xeId/sets on another user's workout exercise returns 404", async () => {
  const cw = await request(app)
    .post("/api/workouts")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Test" });
  const wid = cw.body.id;

  const xe = await request(app)
    .post(`/api/workouts/${wid}/exercises`)
    .set("Authorization", `Bearer ${token}`)
    .send({ exerciseName: "Squat" });

  const reg2 = await request(app)
    .post("/api/auth/register")
    .send({ email: "other@example.com", password: "password123" });
  const token2 = reg2.body.token;

  const res = await request(app)
    .post(`/api/workouts/${wid}/exercises/${xe.body.id}/sets`)
    .set("Authorization", `Bearer ${token2}`)
    .send({ weightKg: 80, reps: 8 });
  expect(res.status).toBe(404);
});

test("GET /progress/weekly counts only workouts inside the week window", async () => {
  const monday = startOfWeek(new Date());

  const inWeek = new Date(monday);
  inWeek.setDate(monday.getDate() + 2);
  inWeek.setHours(19, 30, 0, 0);
  const lastWeek = new Date(monday);
  lastWeek.setDate(monday.getDate() - 3);
  const nextWeek = new Date(monday);
  nextWeek.setDate(monday.getDate() + 8);

  await prisma.workout.create({ data: { ownerId: userId, title: "In week", date: inWeek } });
  await prisma.workout.create({ data: { ownerId: userId, title: "Last week", date: lastWeek } });
  await prisma.workout.create({ data: { ownerId: userId, title: "Next week", date: nextWeek } });

  const res = await request(app)
    .get("/api/workouts/progress/weekly")
    .query({ weekStart: dateKey(monday) })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.weekStart).toBe(monday.getTime());
  expect(res.body.workouts).toBe(1);
  expect(res.body.durationMinutes).toBe(0);
  expect(res.body.caloriesBurned).toBe(0);
  expect(res.body.stepCalories).toBe(0); // steps are on-device only (Health Connect)
  expect(typeof res.body.volume).toBe("number");
});

test("GET /progress/weekly sums set volume (weightKg * reps) for the week", async () => {
  const monday = startOfWeek(new Date());
  const w = await prisma.workout.create({
    data: { ownerId: userId, title: "Volume day", date: monday },
  });
  await prisma.workoutSet.createMany({
    data: [
      { ownerId: userId, workoutId: w.id, exercise: "Bench Press", weightKg: 60, reps: 8 },
      { ownerId: userId, workoutId: w.id, exercise: "Bench Press", weightKg: 62.5, reps: 6 },
    ],
  });

  const res = await request(app)
    .get("/api/workouts/progress/weekly")
    .query({ weekStart: dateKey(monday) })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.workouts).toBe(1);
  expect(res.body.volume).toBe(855); // 60*8 + 62.5*6
});

test("GET /progress/weekly defaults to the current week when weekStart is absent", async () => {
  await prisma.workout.create({ data: { ownerId: userId, title: "Today", date: new Date() } });

  const res = await request(app)
    .get("/api/workouts/progress/weekly")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.workouts).toBe(1);
  expect(res.body.weekStart).toBe(startOfWeek(new Date()).getTime());
});

test("GET /progress/weekly rejects a malformed weekStart", async () => {
  const res = await request(app)
    .get("/api/workouts/progress/weekly")
    .query({ weekStart: "not-a-date" })
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(400);
});

test("GET /progress/weekly returns 401 without a token", async () => {
  const res = await request(app).get("/api/workouts/progress/weekly");
  expect(res.status).toBe(401);
});

test("workout exercises accept supersetGroup on create and via PATCH", async () => {
  const auth = { Authorization: `Bearer ${token}` };
  const wk = await request(app).post("/api/workouts").set(auth).send({ title: "ss" });
  const a = await request(app).post(`/api/workouts/${wk.body.id}/exercises`).set(auth).send({ exerciseName: "Curl", supersetGroup: 1 });
  expect(a.status).toBe(201);
  expect(a.body.supersetGroup).toBe(1);
  const b = await request(app).post(`/api/workouts/${wk.body.id}/exercises`).set(auth).send({ exerciseName: "Pushdown" });
  expect(b.body.supersetGroup).toBeNull();
  const patched = await request(app).patch(`/api/workouts/${wk.body.id}/exercises/${b.body.id}`).set(auth).send({ supersetGroup: 1 });
  expect(patched.status).toBe(200);
  expect(patched.body.supersetGroup).toBe(1);
  const cleared = await request(app).patch(`/api/workouts/${wk.body.id}/exercises/${b.body.id}`).set(auth).send({ supersetGroup: null });
  expect(cleared.body.supersetGroup).toBeNull();
  expect((await request(app).patch(`/api/workouts/${wk.body.id}/exercises/${b.body.id}`).set(auth).send({ supersetGroup: 0 })).status).toBe(400);
  expect((await request(app).post(`/api/workouts/${wk.body.id}/exercises`).set(auth).send({ exerciseName: "X", supersetGroup: 1.5 })).status).toBe(400);
});
