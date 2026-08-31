import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { startOfWeek, computeThisWeekCount } from "./goals";

let token = "";

beforeEach(async () => {
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "goals@example.com", password: "password123" });
  token = reg.body.token as string;
});

async function createGoal(overrides: Record<string, unknown> = {}) {
  const res = await request(app)
    .post("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Read books", targetCount: 2, unit: "times", ...overrides });
  return res;
}

test("POST /api/goals returns 201 and GET lists it with thisWeekCount 0", async () => {
  const create = await createGoal();
  expect(create.status).toBe(201);
  expect(create.body.title).toBe("Read books");
  expect(create.body.thisWeekCount).toBe(0);

  const list = await request(app)
    .get("/api/goals")
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
  expect(list.body[0].thisWeekCount).toBe(0);
  expect(list.body[0].unit).toBe("times");
});

test("POST /api/goals/:id/checkoff twice makes thisWeekCount 2", async () => {
  const create = await createGoal();
  const id = create.body.id;

  await request(app)
    .post(`/api/goals/${id}/checkoff`)
    .set("Authorization", `Bearer ${token}`);
  const second = await request(app)
    .post(`/api/goals/${id}/checkoff`)
    .set("Authorization", `Bearer ${token}`);

  expect(second.status).toBe(200);
  expect(second.body.thisWeekCount).toBe(2);
});

test("POST /api/goals/:id/undo drops thisWeekCount back to 1 and then 0", async () => {
  const create = await createGoal();
  const id = create.body.id;

  await request(app).post(`/api/goals/${id}/checkoff`).set("Authorization", `Bearer ${token}`);
  await request(app).post(`/api/goals/${id}/checkoff`).set("Authorization", `Bearer ${token}`);

  const once = await request(app)
    .post(`/api/goals/${id}/undo`)
    .set("Authorization", `Bearer ${token}`);
  expect(once.status).toBe(200);
  expect(once.body.thisWeekCount).toBe(1);

  const zero = await request(app)
    .post(`/api/goals/${id}/undo`)
    .set("Authorization", `Bearer ${token}`);
  expect(zero.body.thisWeekCount).toBe(0);
});

test("PATCH /api/goals/:id changes targetCount", async () => {
  const create = await createGoal();
  const id = create.body.id;

  const patch = await request(app)
    .patch(`/api/goals/${id}`)
    .set("Authorization", `Bearer ${token}`)
    .send({ targetCount: 5 });
  expect(patch.status).toBe(200);
  expect(patch.body.targetCount).toBe(5);
});

test("DELETE /api/goals/:id returns 204 and list becomes empty", async () => {
  const create = await createGoal();
  const id = create.body.id;

  const del = await request(app)
    .delete(`/api/goals/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);

  const list = await request(app)
    .get("/api/goals")
    .set("Authorization", `Bearer ${token}`);
  expect(list.body).toHaveLength(0);
});

test("GET /api/goals rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/goals");
  expect(res.status).toBe(401);
});

test("startOfWeek returns Monday 00:00:00", () => {
  const monday = startOfWeek(new Date(2026, 7, 31));
  expect(monday.getDay()).toBe(1);
  expect(monday.getFullYear()).toBe(2026);
  expect(monday.getMonth()).toBe(7);
  expect(monday.getDate()).toBe(31);
  expect(monday.getHours()).toBe(0);
  expect(monday.getMinutes()).toBe(0);
  expect(monday.getSeconds()).toBe(0);
  expect(monday.getMilliseconds()).toBe(0);

  const fromThursday = startOfWeek(new Date(2026, 8, 3));
  expect(fromThursday.getDay()).toBe(1);
  expect(fromThursday.getMonth()).toBe(7);
  expect(fromThursday.getDate()).toBe(31);
  expect(fromThursday.getHours()).toBe(0);
});

test("computeThisWeekCount counts only events after startOfWeek", () => {
  const weekStart = startOfWeek(new Date());
  const before = new Date(weekStart.getTime() - 86400000);
  const after = new Date(weekStart.getTime() + 86400000);

  const goal = {
    id: "g1",
    title: "Goal",
    targetCount: 5,
    unit: null,
    category: null,
    autoSource: null,
    events: [
      { date: before, count: 3 },
      { date: after, count: 2 },
    ],
  };

  expect(computeThisWeekCount(goal)).toBe(2);
});
