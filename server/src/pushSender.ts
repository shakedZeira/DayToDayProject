import { PrismaClient } from "@prisma/client";
import webpush from "web-push";
import { prisma as defaultPrisma } from "./db";
import { getVapidKeys } from "./pushKeys";

export interface PushPayload {
  title: string;
  body: string;
  url?: string;
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
        JSON.stringify(payload),
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