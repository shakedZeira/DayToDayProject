import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
const ENDPOINT = "https://push.example.com/fake-endpoint";

beforeEach(async () => {
  await prisma.pushSub.deleteMany({});
  await prisma.task.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "push@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("vapid key returns a non-empty public key for authed user", async () => {
  const res = await request(app)
    .get("/api/push/vapid-key")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(typeof res.body.publicKey).toBe("string");
  expect(res.body.publicKey.length).toBeGreaterThan(0);
});

test("subscribe persists the subscription", async () => {
  const res = await request(app)
    .post("/api/push/subscribe")
    .set("Authorization", `Bearer ${token}`)
    .send({ endpoint: ENDPOINT, keys: { auth: "abc", p256dh: "xyz" } });
  expect(res.status).toBe(200);
  expect(res.body.ok).toBe(true);

  const sub = await prisma.pushSub.findFirst({ where: { endpoint: ENDPOINT } });
  expect(sub).toBeTruthy();
  expect(sub?.keysAuth).toBe("abc");
  expect(sub?.keysP256dh).toBe("xyz");
});

test("subscribe with same endpoint upserts keys", async () => {
  await request(app)
    .post("/api/push/subscribe")
    .set("Authorization", `Bearer ${token}`)
    .send({ endpoint: ENDPOINT, keys: { auth: "abc", p256dh: "xyz" } });
  await request(app)
    .post("/api/push/subscribe")
    .set("Authorization", `Bearer ${token}`)
    .send({ endpoint: ENDPOINT, keys: { auth: "new-auth", p256dh: "new-p256dh" } });

  const subs = await prisma.pushSub.findMany({ where: { endpoint: ENDPOINT } });
  expect(subs).toHaveLength(1);
  expect(subs[0].keysAuth).toBe("new-auth");
  expect(subs[0].keysP256dh).toBe("new-p256dh");
});

test("unsubscribe deletes the subscription", async () => {
  await request(app)
    .post("/api/push/subscribe")
    .set("Authorization", `Bearer ${token}`)
    .send({ endpoint: ENDPOINT, keys: { auth: "abc", p256dh: "xyz" } });

  const res = await request(app)
    .post("/api/push/unsubscribe")
    .set("Authorization", `Bearer ${token}`)
    .send({ endpoint: ENDPOINT });
  expect(res.status).toBe(200);
  expect(res.body.ok).toBe(true);

  const sub = await prisma.pushSub.findFirst({ where: { endpoint: ENDPOINT } });
  expect(sub).toBeNull();
});

test("rejects unauthenticated subscribe", async () => {
  const res = await request(app)
    .post("/api/push/subscribe")
    .send({ endpoint: ENDPOINT, keys: { auth: "abc", p256dh: "xyz" } });
  expect(res.status).toBe(401);
});