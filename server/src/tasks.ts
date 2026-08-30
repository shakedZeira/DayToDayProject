import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const tasksRouter = Router();
tasksRouter.use(requireAuth);

function dueOn(task: { dueAt: Date | null; recurrence: string | null }, date: Date): boolean {
  if (task.recurrence === "none" || task.recurrence == null) {
    return task.dueAt ? sameDay(task.dueAt, date) : false;
  }
  if (task.recurrence === "daily") return true;
  if (task.recurrence.startsWith("weekly:")) {
    const days = task.recurrence.slice("weekly:".length).split(",").map(Number);
    return days.includes(date.getDay());
  }
  return false;
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

tasksRouter.get("/", async (req: AuthedRequest, res) => {
  const tasks = await prisma.task.findMany({ where: { ownerId: req.userId }, orderBy: { createdAt: "desc" } });
  res.json(tasks);
});

tasksRouter.post("/", async (req: AuthedRequest, res) => {
  const { title, notes, dueAt, recurrence, category } = req.body ?? {};
  if (typeof title !== "string" || !title.trim()) {
    return res.status(400).json({ error: "title required" });
  }
  const task = await prisma.task.create({
    data: {
      ownerId: req.userId!,
      title: title.trim(),
      notes: typeof notes === "string" ? notes : null,
      dueAt: dueAt ? new Date(dueAt) : null,
      recurrence: typeof recurrence === "string" ? recurrence : "none",
      category: typeof category === "string" ? category : null
    }
  });
  res.status(201).json(task);
});

tasksRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const id = req.params.id;
  const existing = await prisma.task.findFirst({ where: { id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });

  const { title, notes, status, dueAt, recurrence, category } = req.body ?? {};
  const data: Record<string, unknown> = {};
  if (typeof title === "string") data.title = title.trim();
  if (typeof notes === "string") data.notes = notes;
  if (status === "DONE" || status === "PENDING") {
    data.status = status;
    data.completedAt = status === "DONE" ? new Date() : null;
  }
  if (dueAt !== undefined) data.dueAt = dueAt === null ? null : new Date(dueAt);
  if (typeof recurrence === "string") data.recurrence = recurrence;
  if (category !== undefined) data.category = category === null ? null : category;

  const task = await prisma.task.update({ where: { id }, data });
  res.json(task);
});

tasksRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const id = req.params.id;
  const existing = await prisma.task.findFirst({ where: { id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.task.delete({ where: { id } });
  res.status(204).end();
});

export { dueOn };
