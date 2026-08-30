import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { getVapidKeys } from "./pushKeys";

export const pushRouter = Router();
pushRouter.use(requireAuth);

pushRouter.get("/vapid-key", (_req: AuthedRequest, res) => {
  res.json({ publicKey: getVapidKeys().publicKey });
});

pushRouter.post("/subscribe", async (req: AuthedRequest, res) => {
  const { endpoint, keys } = req.body ?? {};
  if (
    typeof endpoint !== "string" ||
    !endpoint ||
    !keys ||
    typeof keys.auth !== "string" ||
    typeof keys.p256dh !== "string"
  ) {
    return res.status(400).json({ error: "endpoint and keys.auth/keys.p256dh required" });
  }

  const ownerId = req.userId!;
  const existing = await prisma.pushSub.findFirst({ where: { ownerId, endpoint } });
  let sub;
  if (existing) {
    sub = await prisma.pushSub.update({
      where: { id: existing.id },
      data: { keysAuth: keys.auth, keysP256dh: keys.p256dh }
    });
  } else {
    sub = await prisma.pushSub.create({
      data: { ownerId, endpoint, keysAuth: keys.auth, keysP256dh: keys.p256dh }
    });
  }
  res.json({ ok: true, id: sub.id });
});

pushRouter.post("/unsubscribe", async (req: AuthedRequest, res) => {
  const { endpoint } = req.body ?? {};
  if (typeof endpoint !== "string" || !endpoint) {
    return res.status(400).json({ error: "endpoint required" });
  }
  await prisma.pushSub.deleteMany({ where: { ownerId: req.userId!, endpoint } });
  res.json({ ok: true });
});