import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { ITALIAN_COURSE } from "./content/italianCourse";
import { INITIAL_SRS, dueDate, sm2 } from "./italianSrs";
import type { ItalianReviewGradeResult, ItalianReviewItem, ItalianReviewQueue } from "shared";

const EXERCISES = new Map(
  ITALIAN_COURSE.units.flatMap((u) => u.lessons.flatMap((l) => l.exercises.map((e) => [e.id, { lessonId: l.id, exercise: e }] as const)))
);
const LESSONS = new Map(ITALIAN_COURSE.units.flatMap((u) => u.lessons.map((l) => [l.id, l] as const)));
const MAX_LIMIT = 50;

/** Adds a review item for every exercise of every completed lesson (idempotent). */
export async function seedReviewItems(ownerId: string): Promise<void> {
  const completed = await prisma.italianLessonCompletion.findMany({ where: { ownerId }, select: { lessonId: true } });
  const data = completed.flatMap((c) =>
    (LESSONS.get(c.lessonId)?.exercises ?? []).map((e) => ({ ownerId, exerciseId: e.id, lessonId: c.lessonId }))
  );
  if (data.length > 0) await prisma.italianReviewItem.createMany({ data, skipDuplicates: true });
}

export const italianReviewRouter = Router();
italianReviewRouter.use(requireAuth);

italianReviewRouter.get("/review/due", async (req: AuthedRequest, res) => {
  const ownerId = req.userId!;
  const limit = Math.min(MAX_LIMIT, Math.max(1, Number(req.query.limit) || 20));
  await seedReviewItems(ownerId);
  const where = { ownerId, dueAt: { lte: new Date() } };
  const [rows, dueCount, totalItems] = await Promise.all([
    prisma.italianReviewItem.findMany({ where, orderBy: { dueAt: "asc" }, take: limit }),
    prisma.italianReviewItem.count({ where }),
    prisma.italianReviewItem.count({ where: { ownerId } })
  ]);
  const due: ItalianReviewItem[] = rows.flatMap((r) => {
    const ex = EXERCISES.get(r.exerciseId);
    return ex
      ? [{ exerciseId: r.exerciseId, lessonId: r.lessonId, exercise: ex.exercise, ease: r.ease, intervalDays: r.intervalDays, repetitions: r.repetitions, dueAt: r.dueAt.toISOString() }]
      : [];
  });
  const body: ItalianReviewQueue = { due, dueCount, totalItems };
  res.json(body);
});

italianReviewRouter.post("/review/grade", async (req: AuthedRequest, res) => {
  const { exerciseId, grade } = req.body ?? {};
  if (typeof exerciseId !== "string" || typeof grade !== "number" || !Number.isFinite(grade) || grade < 0 || grade > 5) {
    return res.status(400).json({ error: "exerciseId and grade (0-5) required" });
  }
  const ownerId = req.userId!;
  const item = await prisma.italianReviewItem.findUnique({ where: { ownerId_exerciseId: { ownerId, exerciseId } } });
  if (!item) return res.status(404).json({ error: "not found" });
  const next = sm2({ ease: item.ease ?? INITIAL_SRS.ease, intervalDays: item.intervalDays, repetitions: item.repetitions }, grade);
  const now = new Date();
  const updated = await prisma.italianReviewItem.update({
    where: { id: item.id },
    data: { ...next, dueAt: dueDate(now, next.intervalDays), lastReviewedAt: now }
  });
  const body: ItalianReviewGradeResult = {
    exerciseId, ease: updated.ease, intervalDays: updated.intervalDays, repetitions: updated.repetitions, dueAt: updated.dueAt.toISOString()
  };
  res.json(body);
});
