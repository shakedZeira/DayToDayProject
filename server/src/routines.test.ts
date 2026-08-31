import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { seedExercises } from "./exercises.seed";

let token = "";
let exerciseIds: string[] = [];

beforeEach(async () => {
  await prisma.routineExercise.deleteMany({});
  await prisma.routineDay.deleteMany({});
  await prisma.routine.deleteMany({});
  await prisma.exercise.deleteMany({});
  await prisma.user.deleteMany({});
  await seedExercises();

  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "routines@example.com", password: "password123" });
  token = reg.body.token as string;

  // Get a couple of shared exercise IDs
  const exRes = await request(app)
    .get("/api/exercises?q=squat")
    .set("Authorization", `Bearer ${token}`);
  const squat = exRes.body.find((e: { name: string }) => e.name === "Squat");
  const benchRes = await request(app)
    .get("/api/exercises?q=bench")
    .set("Authorization", `Bearer ${token}`);
  const bench = benchRes.body.find((e: { name: string }) => e.name === "Bench Press");
  exerciseIds = [squat.id, bench.id];
});

test("POST /api/routines creates a routine with nested days and exercises", async () => {
  const res = await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "Test Routine",
      description: "A test routine",
      days: [
        {
          name: "Day 1",
          exercises: [
            { order: 0, targetSets: 3, targetReps: 8, exerciseId: exerciseIds[0] },
            { order: 1, targetSets: 3, targetReps: 8, exerciseId: exerciseIds[1] },
          ],
        },
      ],
    });

  expect(res.status).toBe(201);
  expect(res.body.name).toBe("Test Routine");
  expect(res.body.description).toBe("A test routine");
  expect(res.body.days).toHaveLength(1);
  expect(res.body.days[0].name).toBe("Day 1");
  expect(res.body.days[0].exercises).toHaveLength(2);
  expect(res.body.days[0].exercises[0].exercise.name).toBe("Squat");
  expect(res.body.days[0].exercises[1].exercise.name).toBe("Bench Press");
});

test("GET /api/routines lists routines; second user sees empty", async () => {
  await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "My Routine",
      days: [{ name: "Day 1", exercises: [{ order: 0, targetSets: 3, targetReps: 8, exerciseId: exerciseIds[0] }] }],
    });

  const list = await request(app)
    .get("/api/routines")
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);

  // Second user
  const reg2 = await request(app)
    .post("/api/auth/register")
    .send({ email: "other@example.com", password: "password123" });
  const token2 = reg2.body.token as string;
  const list2 = await request(app)
    .get("/api/routines")
    .set("Authorization", `Bearer ${token2}`);
  expect(list2.body).toHaveLength(0);
});

test("PUT /api/routines/:id replaces days", async () => {
  const created = await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "Original",
      days: [{ name: "Day 1", exercises: [{ order: 0, targetSets: 3, targetReps: 8, exerciseId: exerciseIds[0] }] }],
    });
  const id = created.body.id;

  const updated = await request(app)
    .put(`/api/routines/${id}`)
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "Updated",
      days: [
        { name: "New Day A", exercises: [{ order: 0, targetSets: 5, targetReps: 5, exerciseId: exerciseIds[1] }] },
        { name: "New Day B", exercises: [{ order: 0, targetSets: 3, targetReps: 12, exerciseId: exerciseIds[0] }] },
      ],
    });

  expect(updated.status).toBe(200);
  expect(updated.body.name).toBe("Updated");
  expect(updated.body.days).toHaveLength(2);
  expect(updated.body.days[0].name).toBe("New Day A");
  expect(updated.body.days[0].exercises[0].exercise.name).toBe("Bench Press");
  expect(updated.body.days[1].name).toBe("New Day B");
  expect(updated.body.days[1].exercises[0].exercise.name).toBe("Squat");
});

test("DELETE /api/routines/:id removes it", async () => {
  const created = await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "To Delete",
      days: [{ name: "Day 1", exercises: [{ order: 0, targetSets: 3, targetReps: 8, exerciseId: exerciseIds[0] }] }],
    });
  const id = created.body.id;

  const del = await request(app)
    .delete(`/api/routines/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);

  const get = await request(app)
    .get(`/api/routines/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(get.status).toBe(404);
});

test("POST /api/routines validates: missing name -> 400", async () => {
  const res = await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "" });
  expect(res.status).toBe(400);
});

test("POST /api/routines validates: unknown exerciseId -> 400", async () => {
  const res = await request(app)
    .post("/api/routines")
    .set("Authorization", `Bearer ${token}`)
    .send({
      name: "Bad",
      days: [{ name: "Day 1", exercises: [{ order: 0, targetSets: 3, targetReps: 8, exerciseId: "nonexistent" }] }],
    });
  expect(res.status).toBe(400);
  expect(res.body.error).toBe("unknown exercise");
});

test("GET /api/routines rejects unauthenticated", async () => {
  const res = await request(app).get("/api/routines");
  expect(res.status).toBe(401);
});
