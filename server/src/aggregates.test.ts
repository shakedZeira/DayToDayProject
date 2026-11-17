import { beforeEach, test, expect } from "vitest";
import { prisma } from "./db";
import { computeTaskProgress, taskProgressFromDb, consumedCalories } from "./progress";
import { caloriesBetween, italianActiveDays } from "./aggregates";
import { currentStreak } from "./streak";
import { dayRange } from "./digest";

let ownerId = "";

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.task.deleteMany({});
  await prisma.lesson.deleteMany({});
  await prisma.practiceLog.deleteMany({});
  await prisma.user.deleteMany({});
  const u = await prisma.user.create({ data: { email: "agg@example.com", password: "x" } });
  ownerId = u.id;
});

test("taskProgressFromDb matches the in-memory computeTaskProgress", async () => {
  const now = new Date();
  const at = (daysAgo: number, hour: number) => new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysAgo, hour, 30);
  const rows = [
    { status: "DONE", completedAt: at(0, 0), dueAt: null, recurrence: "none" }, // just after local midnight
    { status: "DONE", completedAt: at(0, 23), dueAt: null, recurrence: "none" }, // late evening
    { status: "DONE", completedAt: at(1, 8), dueAt: at(1, 9), recurrence: "none" },
    { status: "DONE", completedAt: at(2, 23), dueAt: null, recurrence: null },
    { status: "DONE", completedAt: at(6, 1), dueAt: null, recurrence: "daily" },
    { status: "DONE", completedAt: at(9, 12), dueAt: null, recurrence: "none" },
    { status: "PENDING", completedAt: null, dueAt: at(3, 12), recurrence: "none" },
    { status: "PENDING", completedAt: null, dueAt: null, recurrence: "weekly:1,3" }
  ];
  for (const r of rows) await prisma.task.create({ data: { ownerId, title: "t", ...r } });
  // another user's data must not leak in
  const other = await prisma.user.create({ data: { email: "agg2@example.com", password: "x" } });
  await prisma.task.create({ data: { ownerId: other.id, title: "o", status: "DONE", completedAt: now } });

  const all = await prisma.task.findMany({
    where: { ownerId },
    select: { status: true, completedAt: true, createdAt: true, dueAt: true, recurrence: true }
  });
  const expected = computeTaskProgress(
    all.map((t) => ({ ...t, status: t.status as "PENDING" | "DONE", recurrence: t.recurrence ?? "none" })),
    now
  );
  expect(await taskProgressFromDb(ownerId, now)).toEqual(expected);
});

test("italianActiveDays and caloriesBetween match the in-memory computations", async () => {
  const now = new Date();
  const local = (daysAgo: number, hour: number) => new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysAgo, hour, 15);
  await prisma.practiceLog.create({ data: { ownerId, lessonDate: "x", phraseIndex: 0, isCorrect: true, userAttempt: "a", createdAt: local(0, 0) } });
  await prisma.practiceLog.create({ data: { ownerId, lessonDate: "x", phraseIndex: 1, isCorrect: true, userAttempt: "a", createdAt: local(0, 22) } });
  await prisma.lesson.create({ data: { ownerId, date: "d1", title: "l", tip: "t", vocab: "[]", phrases: "[]", completedAt: local(1, 23) } });
  await prisma.lesson.create({ data: { ownerId, date: "d2", title: "l", tip: "t", vocab: "[]", phrases: "[]", completedAt: local(2, 1) } });
  await prisma.lesson.create({ data: { ownerId, date: "d3", title: "l", tip: "t", vocab: "[]", phrases: "[]" } });
  const days = await italianActiveDays(ownerId);
  expect(days).toHaveLength(3);
  expect(currentStreak(days, now)).toBe(3);

  const food = await prisma.food.create({ data: { name: "agg-food", caloriesPer100: 123 } });
  const { start, end } = dayRange(now);
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 150, date: local(0, 0) } });
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 80, date: local(0, 20) } });
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 999, date: local(1, 20) } });
  const meals = await prisma.mealLog.findMany({
    where: { ownerId, date: { gte: start, lt: end } },
    include: { food: { select: { caloriesPer100: true } } }
  });
  expect(await caloriesBetween(ownerId, start, end)).toBe(consumedCalories(meals));
  await prisma.food.delete({ where: { id: food.id } });
});
