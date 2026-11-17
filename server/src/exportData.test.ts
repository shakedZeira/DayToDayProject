import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

async function register(email: string) {
  const res = await request(app).post("/api/auth/register").send({ email, password: "password123" });
  expect(res.status).toBe(201);
  return { token: res.body.token as string, id: res.body.user.id as string };
}

let a = { token: "", id: "" };
let b = { token: "", id: "" };

beforeEach(async () => {
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.task.deleteMany({});
  await prisma.setting.deleteMany({});
  await prisma.user.deleteMany({});
  a = await register("export-a@example.com");
  b = await register("export-b@example.com");
});

test("rejects unauthenticated export", async () => {
  const res = await request(app).get("/api/export");
  expect(res.status).toBe(401);
});

test("exports only the caller's data, as a download, without the password hash", async () => {
  await prisma.task.create({ data: { ownerId: a.id, title: "Mine" } });
  await prisma.task.create({ data: { ownerId: b.id, title: "Theirs" } });
  await prisma.setting.create({ data: { ownerId: a.id, key: "italianLevel", value: "A2" } });
  const goal = await prisma.weeklyGoal.create({ data: { ownerId: a.id, title: "Run" } });
  await prisma.goalEvent.create({ data: { ownerId: a.id, goalId: goal.id, source: "manual" } });

  const res = await request(app).get("/api/export").set("Authorization", `Bearer ${a.token}`);
  expect(res.status).toBe(200);
  expect(res.headers["content-disposition"]).toMatch(/attachment; filename="daytoday-export-/);
  const body = JSON.parse(res.text);
  expect(body.user.email).toBe("export-a@example.com");
  expect(body.tasks.map((t: { title: string }) => t.title)).toEqual(["Mine"]);
  expect(body.settings).toEqual({ italianLevel: "A2" });
  expect(body.goals[0].events).toHaveLength(1);
  expect(res.text).not.toMatch(/password/i);
});
