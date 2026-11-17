import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { SettingsResponse } from "shared";
import { isValidTimeZone, parseHHMM } from "./scheduler";

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
  if (key === "notify.enabled" && value !== "true" && value !== "false") {
    return res.status(400).json({ error: "notify.enabled must be true or false" });
  }
  if ((key === "notify.quietStart" || key === "notify.quietEnd") && parseHHMM(value) === null) {
    return res.status(400).json({ error: `${key} must be HH:MM` });
  }
  if (key === "notify.timeZone" && !isValidTimeZone(value)) {
    return res.status(400).json({ error: "notify.timeZone must be an IANA time zone" });
  }
  const row = await prisma.setting.upsert({
    where: { ownerId_key: { ownerId: req.userId!, key } },
    create: { ownerId: req.userId!, key, value },
    update: { value }
  });
  res.json(row);
});
