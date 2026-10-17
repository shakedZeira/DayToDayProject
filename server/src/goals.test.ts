import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { startOfWeek, startOfWeekAtOffset, tzOffsetFromRequest, computeThisWeekCount } from "./goals";

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

// The regression: on a server whose process TZ is UTC (Render, Docker, Vercel,
// Termux-on-phone), `startOfWeek` snapped to Monday 00:00 *UTC*. A user ahead
// of UTC who checks off a goal in the first hours of their Monday produces an
// event that is still *Sunday* in UTC, so `e.date >= weekStart` was false and
// the goal read 0 all week despite the checkoff. These tests pin the offset-
// aware path so the caller's wall-clock Monday is used instead.
function goalWithEvent(date: Date, count = 1) {
  return {
    id: "g1",
    title: "Goal",
    targetCount: 5,
    unit: null,
    category: null,
    autoSource: null,
    events: [{ date, count }],
  };
}

test("startOfWeekAtOffset returns an instant offset from UTC Monday by tzOffsetMinutes", () => {
  // 2026-08-31 is a Monday. At UTC+3 the user's Monday 00:00 is 2026-08-30T21:00Z.
  const utcMidnightMonday = Date.UTC(2026, 7, 31, 0, 0, 0);
  const start = startOfWeekAtOffset(new Date(utcMidnightMonday + 5 * 3600_000), 180);
  expect(start.toISOString()).toBe("2026-08-30T21:00:00.000Z");
  // ...and is independent of the server's local timezone.
  expect(start.getTime()).toBe(utcMidnightMonday - 180 * 60_000);

  // Same instant of "now" yields the *previous* Monday for a UTC+3 caller when
  // now is still Sunday in UTC but already Monday locally.
  const sunday2330Utc = Date.UTC(2026, 7, 30, 23, 30, 0); // Sunday in UTC
  expect(startOfWeekAtOffset(new Date(sunday2330Utc), 180).toISOString()).toBe(
    "2026-08-30T21:00:00.000Z"
  );

  // A west-of-UTC caller's week starts *later* in UTC. At Monday 00:00 UTC the
  // UTC-5 user's wall clock is still Sun 19:00, so the current week began at the
  // prior Monday 05:00 UTC.
  const startWest = startOfWeekAtOffset(new Date(utcMidnightMonday), -300);
  expect(startWest.toISOString()).toBe("2026-08-24T05:00:00.000Z");
  // One hour later (Monday 01:00 UTC = Sun 20:00 UTC-5) the boundary has still
  // not advanced; it advances at 05:00 UTC, when the user's clock hits Monday.
  expect(startOfWeekAtOffset(new Date(utcMidnightMonday + 3600_000), -300).toISOString()).toBe(
    "2026-08-24T05:00:00.000Z"
  );
  expect(startOfWeekAtOffset(new Date(utcMidnightMonday + 5 * 3600_000), -300).toISOString()).toBe(
    "2026-08-31T05:00:00.000Z"
  );
});

test("computeThisWeekCount with a caller offset counts a Monday-early-morning checkoff", () => {
  const utcOffsetMinutes = 180; // caller at UTC+3
  const weekStart = startOfWeekAtOffset(new Date(), utcOffsetMinutes);
  const justAfterMonday = new Date(weekStart.getTime() + 30 * 60_000); // +30 min into caller's Monday
  const justBeforeMonday = new Date(weekStart.getTime() - 60_000); // 1 h before, caller's Sunday

  // This event is genuinely within the caller's week...
  expect(computeThisWeekCount(goalWithEvent(justAfterMonday), utcOffsetMinutes)).toBe(1);
  // ...and an event just before the caller's Monday is excluded.
  expect(computeThisWeekCount(goalWithEvent(justBeforeMonday), utcOffsetMinutes)).toBe(0);
});

test("tzOffsetFromRequest parses valid offsets and rejects malformed ones", () => {
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "180" } })).toBe(180);
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "-300" } })).toBe(-300);
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "0" } })).toBe(0);
  expect(tzOffsetFromRequest({ headers: {} })).toBeUndefined();
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "abc" } })).toBeUndefined();
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "99999" } })).toBeUndefined();
  expect(tzOffsetFromRequest({ headers: { "x-tz-offset": "18.5" } })).toBeUndefined();
});

test("GET /api/goals counts a checkoff at the caller's Monday boundary via x-tz-offset", async () => {
  const create = await createGoal();
  const id = create.body.id;

  // Backdate an event to 30 minutes into the caller's current week.
  const callerOffset = -new Date().getTimezoneOffset();
  const weekStart = startOfWeekAtOffset(new Date(), callerOffset);
  const inWeek = new Date(weekStart.getTime() + 30 * 60_000);
  await prisma.goalEvent.create({
    data: { ownerId: (await prisma.user.findFirst())!.id, goalId: id, date: inWeek, count: 1, source: "manual" },
  });

  const res = await request(app)
    .get("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .set("x-tz-offset", String(callerOffset));
  expect(res.status).toBe(200);
  expect(res.body[0].thisWeekCount).toBe(1);
});
