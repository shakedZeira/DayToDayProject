import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let ownerId = "";

beforeEach(async () => {
  await prisma.setting.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "settings@example.com", password: "password123" });
  token = reg.body.token as string;
  ownerId = reg.body.user.id as string;
});

test("GET settings starts empty", async () => {
  const res = await request(app).get("/api/settings").set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.settings).toEqual({});
});

test("PUT then GET roundtrips a setting", async () => {
  const put = await request(app)
    .put("/api/settings/workoutDays")
    .set("Authorization", `Bearer ${token}`)
    .send({ value: "Mon,Wed,Fri" });
  expect(put.status).toBe(200);

  const get = await request(app).get("/api/settings").set("Authorization", `Bearer ${token}`);
  expect(get.body.settings.workoutDays).toBe("Mon,Wed,Fri");

  const rows = await prisma.setting.findMany({ where: { ownerId } });
  expect(rows).toHaveLength(1);
});

test("PUT rejects missing value", async () => {
  const res = await request(app)
    .put("/api/settings/italianLevel")
    .set("Authorization", `Bearer ${token}`)
    .send({});
  expect(res.status).toBe(400);
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/settings");
  expect(res.status).toBe(401);
});
