import cron from "node-cron";
import { prisma } from "./db";
import { dueOn } from "./tasks";
import { sendPushToUser } from "./pushSender";

let scheduled = false;
const remindedToday = new Set<string>();

function yyyymmdd(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export async function runReminders(now = new Date()): Promise<void> {
  const tasks = await prisma.task.findMany({ where: { status: "PENDING" } });
  const today = yyyymmdd(now);
  for (const task of tasks) {
    if (task.ownerId == null) continue;
    if (!dueOn({ dueAt: task.dueAt, recurrence: task.recurrence }, now)) continue;
    const key = `${task.id}:${today}`;
    if (remindedToday.has(key)) continue;
    remindedToday.add(key);
    await sendPushToUser(task.ownerId, { title: "Reminder", body: task.title, url: "/tasks" });
  }
  for (const key of remindedToday) {
    if (!key.endsWith(`:${today}`)) remindedToday.delete(key);
  }
}

export function startScheduler(): void {
  if (process.env.NODE_ENV === "test") return;
  if (scheduled) return;
  scheduled = true;
  cron.schedule("* * * * *", () => {
    runReminders().catch((err) => console.error("reminder job failed:", err));
  });
}