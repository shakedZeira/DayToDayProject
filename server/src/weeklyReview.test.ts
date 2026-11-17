import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let ownerId = "";
let otherId = "";

const now = new Date();
const at = (daysAgo: number, hour = 12) => new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysAgo, hour, 0);
const key = (daysAgo: number) => {
  const d = at(daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

beforeEach(async () => {
  await prisma.mealLog.deleteMany({});
  await prisma.task.deleteMany({});
  await prisma.workout.deleteMany({});
  await prisma.lesson.deleteMany({});
  await prisma.practiceLog.deleteMany({});
  await prisma.italianLessonCompletion.deleteMany({});
  await prisma.personalRecord.deleteMany({});
  await prisma.dailyTarget.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app).post("/api/auth/register").send({ email: "wr@example.com", password: "password123" });
  token = reg.body.token;
  ownerId = reg.body.user.id;
  otherId = (await prisma.user.create({ data: { email: "wr2@example.com", password: "x" } })).id;
});

const get = (q = "") => request(app).get(`/api/review/weekly${q}`).set("Authorization", `Bearer ${token}`);

test("requires auth and validates date", async () => {
  expect((await request(app).get("/api/review/weekly")).status).toBe(401);
  expect((await get("?date=nope")).status).toBe(400);
});

test("empty week returns zeros and the default calorie target", async () => {
  const res = await get();
  expect(res.status).toBe(200);
  expect(res.body.to).toBe(key(0));
  expect(res.body.from).toBe(key(6));
  expect(res.body.tasks).toEqual({ completed: 0, completedByDay: [0, 0, 0, 0, 0, 0, 0] });
  expect(res.body.workouts).toEqual({ count: 0, target: 2, days: [] });
  expect(res.body.nutrition).toMatchObject({ targetCalories: 2000, daysLogged: 0, avgCalories: 0, diffFromTarget: 0, byDay: [] });
  expect(res.body.italian).toEqual({ lessonsCompleted: 0, xpEarned: 0, streak: 0 });
  expect(res.body.personalRecords).toEqual([]);
});

test("aggregates the 7-day window for the owner only", async () => {
  await prisma.dailyTarget.create({ data: { ownerId, calories: 1800 } });
  for (const [d, n] of [[0, 2], [1, 1], [8, 5]] as const) {
    for (let i = 0; i < n; i++) await prisma.task.create({ data: { ownerId, title: "t", status: "DONE", completedAt: at(d, 9 + i) } });
  }
  await prisma.task.create({ data: { ownerId: otherId, title: "o", status: "DONE", completedAt: at(0) } });

  await prisma.workout.create({ data: { ownerId, title: "a", date: at(1) } });
  await prisma.workout.create({ data: { ownerId, title: "b", date: at(1, 18) } });
  await prisma.workout.create({ data: { ownerId, title: "c", date: at(3) } });
  await prisma.workout.create({ data: { ownerId, title: "old", date: at(10) } });

  const food = await prisma.food.create({ data: { name: "wr-food", caloriesPer100: 100 } });
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 1500, date: at(0) } });
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 1700, date: at(2) } });
  await prisma.mealLog.create({ data: { ownerId, foodId: food.id, grams: 5000, date: at(9) } });

  const lesson = await prisma.lesson.create({ data: { ownerId, date: "d1", title: "l", tip: "t", vocab: "[]", phrases: "[]", completedAt: at(0) } });
  await prisma.italianLessonCompletion.create({ data: { ownerId, lessonId: lesson.id, xpAwarded: 30, completedAt: at(0) } });
  await prisma.italianLessonCompletion.create({ data: { ownerId, lessonId: "old", xpAwarded: 99, completedAt: at(12) } });

  await prisma.personalRecord.create({ data: { ownerId, exerciseName: "bench press", repRange: 5, weight: 80, e1rm: 90, date: at(2) } });
  await prisma.personalRecord.create({ data: { ownerId, exerciseName: "squat", repRange: 5, weight: 100, e1rm: 115, date: at(20) } });

  const res = await get();
  expect(res.body.tasks.completed).toBe(3);
  expect(res.body.tasks.completedByDay[6]).toBe(2);
  expect(res.body.tasks.completedByDay[5]).toBe(1);
  expect(res.body.workouts).toEqual({ count: 3, target: 2, days: [key(3), key(1)] });
  expect(res.body.nutrition.targetCalories).toBe(1800);
  expect(res.body.nutrition.daysLogged).toBe(2);
  expect(res.body.nutrition.byDay).toEqual([
    { date: key(2), calories: 1700 },
    { date: key(0), calories: 1500 }
  ]);
  expect(res.body.nutrition.avgCalories).toBe(1600);
  expect(res.body.nutrition.diffFromTarget).toBe(-200);
  expect(res.body.italian).toEqual({ lessonsCompleted: 1, xpEarned: 30, streak: 1 });
  expect(res.body.personalRecords).toEqual([
    { exerciseName: "bench press", repRange: 5, weight: 80, e1rm: 90, date: key(2) }
  ]);
  await prisma.food.delete({ where: { id: food.id } });
});

test("?date= shifts the window", async () => {
  await prisma.task.create({ data: { ownerId, title: "t", status: "DONE", completedAt: at(10) } });
  const res = await get(`?date=${key(8)}`);
  expect(res.body.to).toBe(key(8));
  expect(res.body.tasks.completed).toBe(1);
  expect(res.body.tasks.completedByDay[4]).toBe(1);
});
