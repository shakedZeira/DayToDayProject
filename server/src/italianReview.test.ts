import { beforeEach, expect, test } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { ITALIAN_COURSE } from "./content/italianCourse";

let token = "";
const lesson = ITALIAN_COURSE.units[0].lessons[0];
const auth = () => ({ Authorization: `Bearer ${token}` });

beforeEach(async () => {
  await prisma.italianReviewItem.deleteMany({});
  await prisma.italianLessonCompletion.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app).post("/api/auth/register").send({ email: "rv@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("no completed lessons means an empty queue", async () => {
  const res = await request(app).get("/api/italian-path/review/due").set(auth());
  expect(res.status).toBe(200);
  expect(res.body).toMatchObject({ dueCount: 0, totalItems: 0, due: [] });
});

test("completed lessons seed due items; grading reschedules them", async () => {
  const me = await prisma.user.findFirstOrThrow({ where: { email: "rv@example.com" } });
  await prisma.italianLessonCompletion.create({ data: { ownerId: me.id, lessonId: lesson.id, xpAwarded: 10 } });
  const res = await request(app).get("/api/italian-path/review/due").set(auth());
  expect(res.body.totalItems).toBe(lesson.exercises.length);
  expect(res.body.due[0].exercise.id).toBeTruthy();

  const id = res.body.due[0].exerciseId as string;
  const g = await request(app).post("/api/italian-path/review/grade").set(auth()).send({ exerciseId: id, grade: 4 });
  expect(g.status).toBe(200);
  expect(g.body.intervalDays).toBe(1);
  expect(new Date(g.body.dueAt).getTime()).toBeGreaterThan(Date.now());

  const again = await request(app).get("/api/italian-path/review/due").set(auth());
  expect(again.body.dueCount).toBe(lesson.exercises.length - 1);
  expect(again.body.totalItems).toBe(lesson.exercises.length);
});

test("grade validates input and unknown items", async () => {
  expect((await request(app).post("/api/italian-path/review/grade").set(auth()).send({ exerciseId: "x", grade: 9 })).status).toBe(400);
  expect((await request(app).post("/api/italian-path/review/grade").set(auth()).send({ exerciseId: "nope", grade: 3 })).status).toBe(404);
});
