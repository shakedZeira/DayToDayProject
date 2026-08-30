import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.task.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "tasks@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("create and list a task", async () => {
  const create = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Buy groceries", recurrence: "daily" });
  expect(create.status).toBe(201);

  const list = await request(app)
    .get("/api/tasks")
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
  expect(list.body[0].title).toBe("Buy groceries");
});

test("complete a task", async () => {
  const create = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Finish report" });
  const id = create.body.id;

  const done = await request(app)
    .patch(`/api/tasks/${id}`)
    .set("Authorization", `Bearer ${token}`)
    .send({ status: "DONE" });
  expect(done.status).toBe(200);
  expect(done.body.status).toBe("DONE");
  expect(done.body.completedAt).toBeTruthy();
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/tasks");
  expect(res.status).toBe(401);
});

test("today returns only tasks due today", async () => {
  const daily = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Morning stretch", recurrence: "daily" });

  const dueToday = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Submit invoice", dueAt: new Date().toISOString() });

  const notToday = await request(app)
    .post("/api/tasks")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Plan next week", dueAt: new Date(Date.now() + 86400000).toISOString() });

  expect(daily.status).toBe(201);
  expect(dueToday.status).toBe(201);
  expect(notToday.status).toBe(201);

  const res = await request(app)
    .get("/api/tasks/today")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  const titles = res.body.map((t: { title: string }) => t.title).sort();
  expect(titles).toEqual(["Morning stretch", "Submit invoice"]);
});

test("dueOn resolves daily recurrence", async () => {
  const { dueOn } = await import("./tasks");
  const daily = { dueAt: null, recurrence: "daily" };
  expect(dueOn(daily, new Date("2026-09-01T00:00:00Z"))).toBe(true);
  const weekly = { dueAt: null, recurrence: "weekly:1,3" };
  expect(dueOn(weekly, new Date("2026-09-01T00:00:00Z"))).toBe(false); // Tuesday(2)
  expect(dueOn(weekly, new Date("2026-09-02T00:00:00Z"))).toBe(true);  // Wednesday(3)
});
