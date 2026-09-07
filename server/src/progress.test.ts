import { test, expect } from "vitest";
import { computeTaskProgress, consumedCalories } from "./progress";

test("consumedCalories computes grams-scaled kcal and rounds", () => {
  const meals = [
    { grams: 200, food: { caloriesPer100: 165 } },
    { grams: 100, food: { caloriesPer100: 61 } }
  ];
  expect(consumedCalories(meals)).toBe(391); // 330 + 61
});

test("computeTaskProgress counts week, rate, streak, and per-day buckets", () => {
  const now = new Date(2026, 8, 1, 12);
  const doneToday = { status: "DONE" as const, completedAt: new Date(2026, 8, 1, 8), createdAt: new Date(2026, 7, 20), dueAt: null, recurrence: "none" };
  const doneYesterday = { status: "DONE" as const, completedAt: new Date(2026, 7, 31, 8), createdAt: new Date(2026, 7, 20), dueAt: null, recurrence: "none" };
  const pending = { status: "PENDING" as const, completedAt: null, createdAt: new Date(2026, 7, 20), dueAt: new Date(2026, 8, 1), recurrence: "none" };
  const r = computeTaskProgress([doneToday, doneYesterday, pending], now);
  expect(r.total).toBe(3);
  expect(r.completedThisWeek).toBe(2);
  expect(r.completionRateWeek).toBe(1);
  expect(r.streak).toBe(2);
  expect(r.completedByDay).toHaveLength(7);
  expect(r.completedByDay[6]).toBe(1); // today slot
});

import { beforeEach, describe } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { todayKey } from "./digest";

describe("GET /api/progress", () => {
  let token = "";
  let ownerId = "";

  beforeEach(async () => {
    await prisma.setting.deleteMany({});
    await prisma.task.deleteMany({});
    await prisma.workout.deleteMany({});
    await prisma.mealLog.deleteMany({});
    await prisma.food.deleteMany({});
    await prisma.dailyTarget.deleteMany({});
    await prisma.highlight.deleteMany({});
    await prisma.flashcard.deleteMany({});
    await prisma.pdf.deleteMany({});
    await prisma.practiceLog.deleteMany({});
    await prisma.lesson.deleteMany({});
    await prisma.user.deleteMany({});

    const reg = await request(app)
      .post("/api/auth/register")
      .send({ email: "progress@example.com", password: "password123" });
    token = reg.body.token as string;
    ownerId = reg.body.user.id as string;

    await prisma.dailyTarget.create({ data: { ownerId, calories: 2500 } });
    await prisma.workout.create({ data: { ownerId, title: "Gym Session", date: new Date() } });
    const food = await prisma.food.create({ data: { name: "Chicken Breast", caloriesPer100: 165 } });
    await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 200, date: new Date() } });
    const pdf = await prisma.pdf.create({
      data: { ownerId, title: "Laplace Notes", fileName: "n.pdf", path: "n", size: 1 }
    });
    await prisma.highlight.create({ data: { ownerId, pdfId: pdf.id, pageNumber: 1, text: "transform pair" } });
    await prisma.flashcard.create({ data: { ownerId, pdfId: pdf.id, front: "What is X?", back: "Y" } });
    await prisma.lesson.create({
      data: { ownerId, date: todayKey(new Date()), title: "Gli articoli", tip: "t", vocab: "[]", phrases: "[]", completedAt: new Date() }
    });
    await prisma.practiceLog.create({
      data: { ownerId, lessonDate: todayKey(new Date()), phraseIndex: 0, isCorrect: true, userAttempt: "ciao" }
    });
  });

  test("aggregates counts, streaks, and reuse of DailyTarget", async () => {
    const res = await request(app)
      .get("/api/progress")
      .set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasks).toMatchObject({ total: 0, completionRateWeek: 0, streak: 0 });
    expect(res.body.health).toMatchObject({ workoutsThisWeek: 1, workoutsTarget: 2, caloriesToday: 330, calorieTarget: 2500 });
    expect(res.body.study).toMatchObject({ pdfs: 1, highlights: 1, flashcards: 1, pdfsInProgress: 0 });
    expect(res.body.italian).toMatchObject({ streak: 1, totalLessons: 1, lessonsThisWeek: 1 });
  });

  test("rejects unauthenticated access", async () => {
    const res = await request(app).get("/api/progress");
    expect(res.status).toBe(401);
  });
});
