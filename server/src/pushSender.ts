import { PrismaClient } from "@prisma/client";
import webpush from "web-push";
import { prisma as defaultPrisma } from "./db";
import { getVapidKeys } from "./pushKeys";

export interface PushPayload {
  title: string;
  body: string;
  url?: string;
  taskId?: string; // lets the client offer a snooze action
  actions?: { action: string; title: string }[];
  snoozeMinutes?: number;
}

export const DEFAULT_SNOOZE_MINUTES = 10;

/** Adds the "Snooze" notification action for task reminders (the service worker handles the click). */
export function withActions(payload: PushPayload): PushPayload {
  if (!payload.taskId || payload.actions) return payload;
  const minutes = payload.snoozeMinutes ?? DEFAULT_SNOOZE_MINUTES;
  return {
    ...payload,
    snoozeMinutes: minutes,
    actions: [{ action: "snooze", title: `Snooze ${minutes} min` }]
  };
}

export async function sendPushToUser(
  userId: string,
  payload: PushPayload,
  prismaLike: PrismaClient = defaultPrisma
): Promise<void> {
  getVapidKeys();
  const subs = await prismaLike.pushSub.findMany({ where: { ownerId: userId } });
  for (const sub of subs) {
    try {
      await webpush.sendNotification(
        { endpoint: sub.endpoint, keys: { auth: sub.keysAuth, p256dh: sub.keysP256dh } },
        JSON.stringify(withActions(payload)),
        { TTL: 60 * 60 }
      );
    } catch (err) {
      const statusCode = (err as { statusCode?: number })?.statusCode;
      if (statusCode === 410 || statusCode === 404) {
        try {
          await prismaLike.pushSub.delete({ where: { id: sub.id } });
        } catch {
          // subscription already gone
        }
      } else {
        console.error(`Web push to subscription ${sub.id} failed:`, err);
      }
    }
  }
}