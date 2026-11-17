import { beforeEach, test, expect, vi } from "vitest";
import request from "supertest";

vi.mock("./pushSender", () => ({ sendPushToUser: vi.fn(async () => {}) }));

import { app } from "./index";
import { prisma } from "./db";
import { sendPushToUser } from "./pushSender";
import { inQuietHours, minutesOfDay, parseHHMM, parseNotifySettings, reminderKind, runReminders } from "./scheduler";

const sendMock = vi.mocked(sendPushToUser);
let token = "";
let ownerId = "";

const base = { id: "t1", dueAt: null, recurrence: "none", remindAt: null, reminderSentAt: null, snoozedUntil: null };
const auth = () => ({ Authorization: `Bearer ${token}` });

beforeEach(async () => {
  sendMock.mockClear();
  await prisma.setting.deleteMany({});
  await prisma.task.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app).post("/api/auth/register").send({ email: "sched@example.com", password: "password123" });
  token = reg.body.token;
  ownerId = reg.body.user.id;
});

test("parseHHMM and inQuietHours handle same-day and overnight windows", () => {
  expect(parseHHMM("22:30")).toBe(1350);
  expect(parseHHMM("24:00")).toBeNull();
  expect(parseHHMM("7:00")).toBeNull();
  expect(inQuietHours(23 * 60, 22 * 60, 7 * 60)).toBe(true);
  expect(inQuietHours(3 * 60, 22 * 60, 7 * 60)).toBe(true);
  expect(inQuietHours(7 * 60, 22 * 60, 7 * 60)).toBe(false);
  expect(inQuietHours(12 * 60, 22 * 60, 7 * 60)).toBe(false);
  expect(inQuietHours(13 * 60, 12 * 60, 14 * 60)).toBe(true);
  expect(inQuietHours(14 * 60, 12 * 60, 14 * 60)).toBe(false);
  expect(inQuietHours(5, 600, 600)).toBe(false);
  expect(inQuietHours(5, null, 600)).toBe(false);
});

test("minutesOfDay honours an IANA time zone", () => {
  const d = new Date("2026-11-17T22:00:00Z");
  expect(minutesOfDay(d, "UTC")).toBe(22 * 60);
  expect(minutesOfDay(d, "Asia/Tokyo")).toBe(7 * 60);
});

test("parseNotifySettings applies defaults and ignores bad values", () => {
  expect(parseNotifySettings({})).toEqual({ enabled: true, quietStart: null, quietEnd: null, timeZone: null });
  expect(parseNotifySettings({ "notify.enabled": "false", "notify.quietStart": "22:00", "notify.quietEnd": "x", "notify.timeZone": "Nope/Zone" }))
    .toEqual({ enabled: false, quietStart: 1320, quietEnd: null, timeZone: null });
});

test("reminderKind: explicit, snooze, daily and recurring time gate", () => {
  const now = new Date(2026, 10, 17, 12, 0);
  const earlier = new Date(2026, 10, 17, 9, 0);
  const later = new Date(2026, 10, 17, 18, 0);
  expect(reminderKind({ ...base, remindAt: earlier }, now, new Set())).toBe("explicit");
  expect(reminderKind({ ...base, remindAt: later }, now, new Set())).toBeNull();
  expect(reminderKind({ ...base, remindAt: earlier, reminderSentAt: earlier }, now, new Set())).toBeNull();
  // a due task with a future explicit remindAt must not fire from the daily rule
  expect(reminderKind({ ...base, dueAt: now, remindAt: later }, now, new Set())).toBeNull();

  expect(reminderKind({ ...base, dueAt: now, snoozedUntil: later }, now, new Set())).toBeNull();
  expect(reminderKind({ ...base, dueAt: now, snoozedUntil: earlier }, now, new Set())).toBe("snooze");

  expect(reminderKind({ ...base, dueAt: now }, now, new Set())).toBe("daily");
  expect(reminderKind({ ...base, dueAt: now }, now, new Set(["t1:2026-11-17"]))).toBeNull();
  expect(reminderKind({ ...base, recurrence: "daily" }, now, new Set())).toBe("daily");
  expect(reminderKind({ ...base, recurrence: "daily", remindAt: later }, now, new Set())).toBeNull();
  expect(reminderKind({ ...base, recurrence: "daily", remindAt: earlier }, now, new Set())).toBe("daily");
  expect(reminderKind(base, now, new Set())).toBeNull();
});

test("runReminders sends an explicit reminder once and persists reminderSentAt", async () => {
  const now = new Date();
  const t = await prisma.task.create({ data: { ownerId, title: "Call mom", remindAt: new Date(now.getTime() - 60_000) } });
  await runReminders(now);
  await runReminders(now);
  expect(sendMock).toHaveBeenCalledTimes(1);
  expect(sendMock.mock.calls[0][1]).toMatchObject({ body: "Call mom", taskId: t.id });
  expect((await prisma.task.findUnique({ where: { id: t.id } }))?.reminderSentAt).not.toBeNull();
});

test("quiet hours and disabled hold reminders without consuming them", async () => {
  const now = new Date();
  const t = await prisma.task.create({ data: { ownerId, title: "Quiet", remindAt: new Date(now.getTime() - 60_000) } });
  const mins = minutesOfDay(now, null);
  const hh = (m: number) => `${String(Math.floor(((m % 1440) + 1440) % 1440 / 60)).padStart(2, "0")}:${String((((m % 1440) + 1440) % 1440) % 60).padStart(2, "0")}`;
  await prisma.setting.create({ data: { ownerId, key: "notify.quietStart", value: hh(mins - 30) } });
  await prisma.setting.create({ data: { ownerId, key: "notify.quietEnd", value: hh(mins + 30) } });
  await runReminders(now);
  expect(sendMock).not.toHaveBeenCalled();
  expect((await prisma.task.findUnique({ where: { id: t.id } }))?.reminderSentAt).toBeNull();

  // quiet window ends -> it goes out
  await runReminders(new Date(now.getTime() + 2 * 3600_000));
  expect(sendMock).toHaveBeenCalledTimes(1);

  // disabled: nothing is sent
  sendMock.mockClear();
  await prisma.task.update({ where: { id: t.id }, data: { reminderSentAt: null } });
  await prisma.setting.create({ data: { ownerId, key: "notify.enabled", value: "false" } });
  await runReminders(new Date(now.getTime() + 2 * 3600_000));
  expect(sendMock).not.toHaveBeenCalled();
});

test("snooze: API sets snoozedUntil, scheduler waits then fires once", async () => {
  const t = await prisma.task.create({ data: { ownerId, title: "Snooze me", dueAt: new Date() } });
  const res = await request(app).post(`/api/tasks/${t.id}/snooze`).set(auth()).send({ minutes: 30 });
  expect(res.status).toBe(200);
  const until = new Date(res.body.snoozedUntil).getTime();
  expect(until - Date.now()).toBeGreaterThan(29 * 60_000);

  await runReminders(new Date());
  expect(sendMock).not.toHaveBeenCalled();
  const after = new Date(until + 1000);
  await runReminders(after);
  expect(sendMock).toHaveBeenCalledTimes(1);
  expect(sendMock.mock.calls[0][1].title).toContain("snoozed");
  await runReminders(new Date(until + 120_000));
  expect(sendMock).toHaveBeenCalledTimes(1); // not repeated by the daily rule
  expect((await prisma.task.findUnique({ where: { id: t.id } }))?.snoozedUntil).toBeNull();
});

test("snooze validates input and ownership", async () => {
  const t = await prisma.task.create({ data: { ownerId, title: "x" } });
  expect((await request(app).post(`/api/tasks/${t.id}/snooze`).set(auth()).send({ minutes: 0 })).status).toBe(400);
  expect((await request(app).post(`/api/tasks/${t.id}/snooze`).set(auth()).send({ minutes: 5000 })).status).toBe(400);
  expect((await request(app).post(`/api/tasks/nope/snooze`).set(auth()).send({})).status).toBe(404);
  const other = await request(app).post("/api/auth/register").send({ email: "sched2@example.com", password: "password123" });
  expect((await request(app).post(`/api/tasks/${t.id}/snooze`).set({ Authorization: `Bearer ${other.body.token}` }).send({})).status).toBe(404);
  expect((await request(app).post(`/api/tasks/${t.id}/snooze`).set(auth()).send({})).status).toBe(200);
});

test("tasks accept remindAt; changing it re-arms and clears a snooze", async () => {
  const when = new Date(Date.now() + 3600_000).toISOString();
  const created = await request(app).post("/api/tasks").set(auth()).send({ title: "R", remindAt: when });
  expect(created.status).toBe(201);
  expect(new Date(created.body.remindAt).toISOString()).toBe(when);
  expect((await request(app).post("/api/tasks").set(auth()).send({ title: "R", remindAt: "garbage" })).status).toBe(400);

  await request(app).post(`/api/tasks/${created.body.id}/snooze`).set(auth()).send({ minutes: 60 });
  await prisma.task.update({ where: { id: created.body.id }, data: { reminderSentAt: new Date() } });
  const patched = await request(app).patch(`/api/tasks/${created.body.id}`).set(auth()).send({ remindAt: null });
  expect(patched.body).toMatchObject({ remindAt: null, reminderSentAt: null, snoozedUntil: null });
});

test("notify settings are validated", async () => {
  const put = (key: string, value: string) => request(app).put(`/api/settings/${key}`).set(auth()).send({ value });
  expect((await put("notify.quietStart", "22:00")).status).toBe(200);
  expect((await put("notify.quietStart", "10pm")).status).toBe(400);
  expect((await put("notify.enabled", "maybe")).status).toBe(400);
  expect((await put("notify.timeZone", "Asia/Jerusalem")).status).toBe(200);
  expect((await put("notify.timeZone", "Mars/Base")).status).toBe(400);
});
