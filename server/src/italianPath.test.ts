import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";
import { ITALIAN_COURSE } from "./content/italianCourse";
import type { ItalianAnswer } from "shared";

let token = "";

function allCorrectAnswers(lessonId: string): ItalianAnswer[] {
  const lesson = ITALIAN_COURSE.units
    .flatMap((u) => u.lessons)
    .find((l) => l.id === lessonId)!;
  return lesson.exercises.map((ex) => {
    if (ex.kind === "choice") return { exerciseId: ex.id, kind: "choice", choiceIndex: ex.answerIndex };
    if (ex.kind === "type") return { exerciseId: ex.id, kind: "type", text: ex.accepted[0] };
    return { exerciseId: ex.id, kind: "match", pairs: ex.pairs };
  });
}

beforeEach(async () => {
  await prisma.italianExerciseAttempt.deleteMany({});
  await prisma.italianLessonCompletion.deleteMany({});
  await prisma.goalEvent.deleteMany({});
  await prisma.weeklyGoal.deleteMany({});
  await prisma.lesson.deleteMany({});
  await prisma.practiceLog.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "ip@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("course returns 3 units with lessons", async () => {
  const res = await request(app)
    .get("/api/italian-path/course")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(200);
  expect(res.body.units).toHaveLength(3);
  const lessons = res.body.units.flatMap((u: { lessons: unknown[] }) => u.lessons);
  expect(lessons).toHaveLength(9);
  for (const l of lessons as { exercises: unknown[] }[]) {
    expect(l.exercises).toHaveLength(6);
  }
});

test("unauthenticated is rejected", async () => {
  expect((await request(app).get("/api/italian-path/course")).status).toBe(401);
  expect((await request(app).get("/api/italian-path/progress")).status).toBe(401);
});

test("submitting all correct answers completes lesson, awards xp, emits goal events", async () => {
  await request(app)
    .post("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Italian lessons", targetCount: 3, autoSource: "italian_lesson" });
  await request(app)
    .post("/api/goals")
    .set("Authorization", `Bearer ${token}`)
    .send({ title: "Italian XP", targetCount: 100, autoSource: "italian_xp" });

  const res = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(res.status).toBe(201);
  expect(res.body.lessonCompleted).toBe(true);
  expect(res.body.xpGained).toBe(6 + 10);
  expect(res.body.progress.completedLessonIds).toContain("u1l1");
  expect(res.body.progress.xp).toBe(16);

  const goals = await request(app)
    .get("/api/goals")
    .set("Authorization", `Bearer ${token}`);
  const lessonGoal = goals.body.find((g: { autoSource: string }) => g.autoSource === "italian_lesson");
  const xpGoal = goals.body.find((g: { autoSource: string }) => g.autoSource === "italian_xp");
  expect(lessonGoal.thisWeekCount).toBe(1);
  expect(xpGoal.thisWeekCount).toBe(16);
});

test("replaying a completed lesson awards replay xp only", async () => {
  const first = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(first.body.xpGained).toBe(16);

  const second = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  expect(second.body.lessonCompleted).toBe(false);
  expect(second.body.xpGained).toBe(5);
  expect(second.body.progress.xp).toBe(21);
});

test("wrong answers do not complete the lesson and count for review", async () => {
  const answers: ItalianAnswer[] = [
    { exerciseId: "u1l1e1", kind: "choice", choiceIndex: 1 },
    { exerciseId: "u1l1e2", kind: "choice", choiceIndex: 1 },
    { exerciseId: "u1l1e3", kind: "type", text: "grazie" },
    { exerciseId: "u1l1e4", kind: "match", pairs: ITALIAN_COURSE.units[0].lessons[0].exercises[3].kind === "match" ? ITALIAN_COURSE.units[0].lessons[0].exercises[3].pairs : [] },
    { exerciseId: "u1l1e5", kind: "choice", choiceIndex: 2 },
    { exerciseId: "u1l1e6", kind: "choice", choiceIndex: 0 }
  ];
  const res = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers });
  expect(res.body.lessonCompleted).toBe(false);
  const review = res.body.progress.reviewDue as { exerciseId: string }[];
  expect(review.map((r) => r.exerciseId)).toEqual(
    expect.arrayContaining(["u1l1e1", "u1l1e5"])
  );
  expect(review.map((r) => r.exerciseId)).not.toContain("u1l1e3");
});

test("rejects unknown lesson and unknown exercise", async () => {
  const a = await request(app)
    .post("/api/italian-path/lesson/nope/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: [] });
  expect(a.status).toBe(404);

  const b = await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: [{ exerciseId: "zzz", kind: "choice", choiceIndex: 0 }] });
  expect(b.status).toBe(400);
});

test("streak is 1 after activity today", async () => {
  await request(app)
    .post("/api/italian-path/lesson/u1l1/submit")
    .set("Authorization", `Bearer ${token}`)
    .send({ answers: allCorrectAnswers("u1l1") });
  const res = await request(app)
    .get("/api/italian-path/progress")
    .set("Authorization", `Bearer ${token}`);
  expect(res.body.streak).toBe(1);
  expect(res.body.totalLessons).toBe(9);
});
