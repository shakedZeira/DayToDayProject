import { authedFetch } from "./auth";
import type { SettingsResponse } from "shared";

export async function getSettings(token: string): Promise<SettingsResponse> {
  const res = await authedFetch(token, "/api/settings");
  if (!res.ok) throw new Error("failed to load settings");
  return res.json();
}

export async function putSetting(token: string, key: string, value: string): Promise<void> {
  const res = await authedFetch(token, `/api/settings/${encodeURIComponent(key)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ value })
  });
  if (!res.ok) throw new Error("failed to save setting");
}
