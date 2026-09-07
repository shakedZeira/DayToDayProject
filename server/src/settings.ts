import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { SettingsResponse } from "shared";

export const settingsRouter = Router();
settingsRouter.use(requireAuth);

settingsRouter.get("/", async (req: AuthedRequest, res) => {
  const rows = await prisma.setting.findMany({ where: { ownerId: req.userId } });
  const settings: Record<string, string> = {};
  for (const row of rows) settings[row.key] = row.value;
  const body: SettingsResponse = { settings };
  res.json(body);
});

settingsRouter.put("/:key", async (req: AuthedRequest, res) => {
  const { value } = req.body ?? {};
  if (typeof value !== "string") {
    return res.status(400).json({ error: "value required" });
  }
  const key = String(req.params.key);
  const row = await prisma.setting.upsert({
    where: { ownerId_key: { ownerId: req.userId!, key } },
    create: { ownerId: req.userId!, key, value },
    update: { value }
  });
  res.json(row);
});
