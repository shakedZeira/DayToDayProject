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

// ---- Notification settings (stored as Setting rows with a "notify." prefix) ----

export interface NotifySettings {
  enabled: boolean;
  quietStart: number | null; // minutes since local midnight
  quietEnd: number | null;
  timeZone: string | null; // IANA zone; null = server local time
}

export const NOTIFY_KEYS = ["notify.enabled", "notify.quietStart", "notify.quietEnd", "notify.timeZone"] as const;

export function parseHHMM(v: string | undefined): number | null {
  const m = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(v ?? "");
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

export function isValidTimeZone(tz: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

export function parseNotifySettings(map: Record<string, string>): NotifySettings {
  const tz = map["notify.timeZone"];
  return {
    enabled: map["notify.enabled"] !== "false",
    quietStart: parseHHMM(map["notify.quietStart"]),
    quietEnd: parseHHMM(map["notify.quietEnd"]),
    timeZone: tz && isValidTimeZone(tz) ? tz : null
  };
}

export function minutesOfDay(d: Date, timeZone: string | null): number {
  if (!timeZone) return d.getHours() * 60 + d.getMinutes();
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).formatToParts(d);
  const h = Number(parts.find((p) => p.type === "hour")?.value);
  const m = Number(parts.find((p) => p.type === "minute")?.value);
  return h * 60 + m;
}

/** Quiet window [start, end); wraps past midnight when start > end; empty when equal or unset. */
export function inQuietHours(minutes: number, start: number | null, end: number | null): boolean {
  if (start === null || end === null || start === end) return false;
  return start < end ? minutes >= start && minutes < end : minutes >= start || minutes < end;
}

// ---- Which reminders are due ----

export interface ReminderTask {
  id: string;
  dueAt: Date | null;
  recurrence: string | null;
  remindAt: Date | null;
  reminderSentAt: Date | null;
  snoozedUntil: Date | null;
}

export type ReminderKind = "snooze" | "explicit" | "daily";

/**
 * Pure decision: should this pending task be announced now, ignoring quiet hours?
 * - a snooze suppresses everything until it expires, then fires once ("snooze");
 * - remindAt fires once when reached ("explicit"); for recurring tasks it is a time-of-day gate on due days;
 * - otherwise a task due today is announced once per day ("daily").
 */
export function reminderKind(
  task: ReminderTask,
  now: Date,
  remindedKeys: Set<string>,
  timeZone: string | null = null
): ReminderKind | null {
  if (task.snoozedUntil) return task.snoozedUntil <= now ? "snooze" : null;

  const recurring = task.recurrence != null && task.recurrence !== "none";
  const dailyKey = `${task.id}:${yyyymmdd(now)}`;
  if (task.remindAt && !recurring) {
    const sent = task.reminderSentAt != null && task.reminderSentAt >= task.remindAt;
    return task.remindAt <= now && !sent ? "explicit" : null;
  }
  if (!dueOn({ dueAt: task.dueAt, recurrence: task.recurrence }, now)) return null;
  if (remindedKeys.has(dailyKey)) return null;
  if (task.remindAt && minutesOfDay(now, timeZone) < minutesOfDay(task.remindAt, timeZone)) return null;
  return "daily";
}

export async function runReminders(now = new Date()): Promise<void> {
  const tasks = await prisma.task.findMany({ where: { status: "PENDING" } });
  const today = yyyymmdd(now);
  const ownerIds = [...new Set(tasks.map((t) => t.ownerId))];
  const settingRows = await prisma.setting.findMany({
    where: { ownerId: { in: ownerIds }, key: { in: [...NOTIFY_KEYS] } }
  });
  const settingsByOwner = new Map<string, NotifySettings>();
  for (const id of ownerIds) {
    const map: Record<string, string> = {};
    for (const r of settingRows) if (r.ownerId === id) map[r.key] = r.value;
    settingsByOwner.set(id, parseNotifySettings(map));
  }

  for (const task of tasks) {
    if (task.ownerId == null) continue;
    const settings = settingsByOwner.get(task.ownerId) as NotifySettings;
    const kind = reminderKind(task, now, remindedToday, settings.timeZone);
    if (!kind) continue;
    // Disabled / quiet hours: leave state untouched so the reminder goes out once allowed.
    if (!settings.enabled) continue;
    if (inQuietHours(minutesOfDay(now, settings.timeZone), settings.quietStart, settings.quietEnd)) continue;

    remindedToday.add(`${task.id}:${today}`); // a snooze or explicit reminder also counts as today's announcement
    if (kind !== "daily") {
      await prisma.task.update({
        where: { id: task.id },
        data: { reminderSentAt: now, ...(kind === "snooze" ? { snoozedUntil: null } : {}) }
      });
    }
    await sendPushToUser(task.ownerId, {
      title: kind === "snooze" ? "Reminder (snoozed)" : "Reminder",
      body: task.title,
      url: "/tasks",
      taskId: task.id
    });
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
