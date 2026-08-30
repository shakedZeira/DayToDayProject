import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

beforeEach(async () => {
  await prisma.user.deleteMany({});
});

test("register and login roundtrip", async () => {
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "me@example.com", password: "password123" });
  expect(reg.status).toBe(201);
  expect(reg.body.token).toBeTruthy();

  const login = await request(app)
    .post("/api/auth/login")
    .send({ email: "me@example.com", password: "password123" });
  expect(login.status).toBe(200);
  expect(login.body.user.email).toBe("me@example.com");
});

test("rejects short password", async () => {
  const res = await request(app)
    .post("/api/auth/register")
    .send({ email: "me@example.com", password: "short" });
  expect(res.status).toBe(400);
});
