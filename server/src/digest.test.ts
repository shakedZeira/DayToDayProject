import { test, expect, describe, beforeEach } from "vitest";
import request from "supertest";
import { buildDigest, workoutReminder, todayKey, dayRange } from "./digest";
import { app } from "./index";
import { prisma } from "./db";

test("workoutReminder returns null when on track", () => {
  expect(workoutReminder(2, 0)).toBeNull();
  expect(workoutReminder(1, 2)).toBeNull();
});

test("workoutReminder nags when behind", () => {
  expect(workoutReminder(1, 4)).toBe("3+ days since your last workout — schedule a session.");
  expect(workoutReminder(0, null)).toBe("No workouts this week yet — aim for 2.");
});

test("buildDigest assembles a full response", () => {
  const res = buildDigest({
    today: "2026-09-01",
    tasksToday: [
      { id: "t1", title: "Pay rent", category: null, recurrence: "none", status: "PENDING", dueAt: null, completedAt: null }
    ],
    workoutsThisWeek: 1,
    lastWorkoutDaysAgo: 3,
    lessonTitle: "Ciao",
    activeDays: [new Date(2026, 8, 1)],
    totalLessons: 5
  });
  expect(res.topTask?.title).toBe("Pay rent");
  expect(res.tasksToday).toHaveLength(1);
  expect(res.health.reminder).toBe("3+ days since your last workout — schedule a session.");
  expect(res.italian.streak).toBe(1);
  expect(res.italian.totalLessons).toBe(5);
});

test("todayKey and dayRange are local-date aligned", () => {
  const now = new Date(2026, 8, 1, 10, 30);
  expect(todayKey(now)).toBe("2026-09-01");
  const range = dayRange(now);
  expect(range.start.getTime()).toBe(new Date(2026, 8, 1).getTime());
  expect(range.end.getTime()).toBe(new Date(2026, 8, 2).getTime());
});

describe("GET /api/digest/today", () => {
  let token = "";
  let ownerId = "";

  beforeEach(async () => {
    await prisma.task.deleteMany({});
    await prisma.workout.deleteMany({});
    await prisma.lesson.deleteMany({});
    await prisma.practiceLog.deleteMany({});
    await prisma.user.deleteMany({});

    const reg = await request(app)
      .post("/api/auth/register")
      .send({ email: "digest@example.com", password: "password123" });
    token = reg.body.token as string;
    ownerId = reg.body.user.id as string;

    await prisma.task.create({
      data: { ownerId, title: "Pay rent", status: "PENDING", dueAt: new Date() }
    });
    await prisma.workout.create({ data: { ownerId, title: "Leg day", date: new Date(Date.now() - 4 * 86_400_000) } });
    await prisma.lesson.create({
      data: { ownerId, date: todayKey(new Date()), title: "Il caffè", tip: "t", vocab: "[]", phrases: "[]", completedAt: new Date() }
    });
    await prisma.practiceLog.create({
      data: { ownerId, lessonDate: todayKey(new Date()), phraseIndex: 0, isCorrect: true, userAttempt: "ciao" }
    });
  });

  test("aggregates today's digest across all modules", async () => {
    const res = await request(app)
      .get("/api/digest/today")
      .set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasksToday).toHaveLength(1);
    expect(res.body.topTask.title).toBe("Pay rent");
    expect(res.body.health.workoutsThisWeek).toBe(1);
    expect(res.body.health.reminder).toBe("3+ days since your last workout — schedule a session.");
    expect(res.body.italian.lessonTitle).toBe("Il caffè");
    expect(res.body.italian.streak).toBe(1);
    expect(res.body.italian.totalLessons).toBe(1);
  });

  test("rejects unauthenticated access", async () => {
    const res = await request(app).get("/api/digest/today");
    expect(res.status).toBe(401);
  });
});
