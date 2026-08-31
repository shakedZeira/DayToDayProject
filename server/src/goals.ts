import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { WeeklyGoalView } from "shared";

export const goalsRouter = Router();
goalsRouter.use(requireAuth);

export function startOfWeek(now: Date): Date {
  const day = now.getDay();
  const diff = (day + 6) % 7;
  const monday = new Date(now);
  monday.setDate(now.getDate() - diff);
  monday.setHours(0, 0, 0, 0);
  return monday;
}

interface GoalWithEvents {
  id: string;
  title: string;
  targetCount: number;
  unit: string | null;
  category: string | null;
  autoSource: string | null;
  events: { date: Date; count: number }[];
}

export function computeThisWeekCount(goal: GoalWithEvents): number {
  const weekStart = startOfWeek(new Date());
  return goal.events
    .filter((e) => e.date >= weekStart)
    .reduce((sum, e) => sum + e.count, 0);
}

function toView(goal: GoalWithEvents): WeeklyGoalView {
  const events = [...goal.events].sort((a, b) => b.date.getTime() - a.date.getTime());
  const last = events[0];
  return {
    id: goal.id,
    title: goal.title,
    targetCount: goal.targetCount,
    unit: goal.unit,
    category: goal.category,
    autoSource: goal.autoSource,
    thisWeekCount: computeThisWeekCount(goal),
    lastEventAt: last ? last.date.toISOString() : null,
  };
}

async function findOwnedWithEvents(id: string, ownerId: string) {
  const goal = await prisma.weeklyGoal.findFirst({
    where: { id, ownerId },
    include: { events: { select: { date: true, count: true }, orderBy: { date: "desc" } } },
  });
  return goal;
}

goalsRouter.get("/", async (req: AuthedRequest, res) => {
  const goals = await prisma.weeklyGoal.findMany({
    where: { ownerId: req.userId },
    include: { events: { select: { date: true, count: true }, orderBy: { date: "desc" } } },
    orderBy: { createdAt: "desc" },
  });
  res.json(goals.map(toView));
});

goalsRouter.post("/", async (req: AuthedRequest, res) => {
  const { title, targetCount, unit, category, autoSource } = req.body ?? {};
  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  const count =
    typeof targetCount === "number" && Number.isFinite(targetCount) && targetCount >= 1
      ? Math.floor(targetCount)
      : 1;
  const goal = await prisma.weeklyGoal.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      targetCount: count,
      unit: typeof unit === "string" && unit.trim() ? unit.trim() : null,
      category: typeof category === "string" && category.trim() ? category.trim() : null,
      autoSource: typeof autoSource === "string" && autoSource.trim() ? autoSource.trim() : null,
    },
  });
  const withEvents = await findOwnedWithEvents(goal.id, req.userId!);
  res.status(201).json(withEvents ? toView(withEvents) : goal);
});

goalsRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.weeklyGoal.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });

  const { title, targetCount, unit, category, autoSource } = req.body ?? {};
  const data: Record<string, unknown> = {};
  if (typeof title === "string" && title.trim()) data.title = title.trim();
  if (targetCount !== undefined) {
    const count =
      typeof targetCount === "number" && Number.isFinite(targetCount) && targetCount >= 1
        ? Math.floor(targetCount)
        : null;
    if (count !== null) data.targetCount = count;
  }
  if (unit !== undefined) data.unit = unit === null ? null : typeof unit === "string" ? unit.trim() || null : null;
  if (category !== undefined) data.category = category === null ? null : typeof category === "string" ? category.trim() || null : null;
  if (autoSource !== undefined) data.autoSource = autoSource === null ? null : typeof autoSource === "string" ? autoSource.trim() || null : null;

  const goal = await prisma.weeklyGoal.update({ where: { id: existing.id }, data });
  const withEvents = await findOwnedWithEvents(goal.id, req.userId!);
  res.json(withEvents ? toView(withEvents) : goal);
});

goalsRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.weeklyGoal.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.weeklyGoal.delete({ where: { id: existing.id } });
  res.status(204).end();
});

goalsRouter.post("/:id/checkoff", async (req: AuthedRequest, res) => {
  const existing = await prisma.weeklyGoal.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.goalEvent.create({
    data: {
      ownerId: req.userId!,
      goalId: existing.id,
      date: new Date(),
      count: 1,
      source: "manual",
    },
  });
  const withEvents = await findOwnedWithEvents(existing.id, req.userId!);
  res.json(withEvents ? toView(withEvents) : existing);
});

goalsRouter.post("/:id/undo", async (req: AuthedRequest, res) => {
  const existing = await prisma.weeklyGoal.findFirst({
    where: { id: req.params.id, ownerId: req.userId },
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  const latest = await prisma.goalEvent.findFirst({
    where: { goalId: existing.id, ownerId: req.userId },
    orderBy: { date: "desc" },
  });
  if (latest) {
    await prisma.goalEvent.delete({ where: { id: latest.id } });
  }
  const withEvents = await findOwnedWithEvents(existing.id, req.userId!);
  res.json(withEvents ? toView(withEvents) : existing);
});
